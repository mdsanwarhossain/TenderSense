package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.WorldBankProperties;
import com.bracit.tendersense.dto.FetchResult;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.exception.FetchException;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.service.TenderFetchService;
import com.bracit.tendersense.util.HashUtil;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * World Bank procurement notices. No scraping: this is a public JSON API.
 *
 * <p>Filtering uses {@code project_ctry_name_exact}. Two similarly-named parameters,
 * {@code countryname_exact} and {@code cntry_exact}, are accepted by the API and then
 * silently ignored, returning the full ~418k corpus -- a filter that looks like it
 * works while doing nothing. {@link #assertFilterApplied} exists to catch exactly that.
 *
 * <p>Three things this gets right that are easy to get wrong:
 * <ul>
 *   <li><b>Newest first.</b> The API's default order is neither by notice date nor by
 *       deadline, so an incremental sweep that stops at a page of known ids could read
 *       the same old page for ever and never see a new notice. Every call sorts.</li>
 *   <li><b>The deadline is {@code submission_deadline_date}</b>, with its own time
 *       (15:00, 14:00 ...). The similarly-named {@code submission_date} is the
 *       publication date wearing a deadline's name -- it matches {@code noticedate} on
 *       every Bangladesh notice checked, which is how a corpus ends up looking entirely
 *       closed.</li>
 *   <li><b>One call per notice type.</b> The API honours a single
 *       {@code notice_type_exact} and ignores the rest, so the types are fetched one at
 *       a time (see {@link WorldBankProperties#getNoticeTypes()}).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorldBankTenderFetchServiceImpl implements TenderFetchService {

    /** Bumped when the parsing changes, so stored notices are re-read on the next reconcile. */
    static final String PARSER_VERSION = "wb-2";

    private static final DateTimeFormatter NOTICE_DATE =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter PROSE_DATE =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    /** Newest published first, per notice type. */
    private static final String SORT = "noticedate desc,id asc";

    /** "Tender/Proposal ID : 1257451" in the e-GP notice these often copy. */
    private static final Pattern EGP_ID =
            Pattern.compile("Tender\\s*/\\s*Proposal\\s*ID\\s*:?\\s*(\\d{5,})", Pattern.CASE_INSENSITIVE);
    /** "For record only, deadline for bid submission was May 7, 2026." */
    private static final Pattern PROSE_DEADLINE = Pattern.compile(
            "deadline for [a-z ]{0,40}submission\\s+(?:was|is)\\s+([A-Z][a-z]+ \\d{1,2}, \\d{4})",
            Pattern.CASE_INSENSITIVE);

    /** The API answers 500 to perfectly good requests now and then; 4 tries span ~3.5s. */
    private static final int MAX_ATTEMPTS = 4;
    private static final long BASE_BACKOFF_MS = 500;

    private final RestClient restClient;
    private final WorldBankProperties props;
    private final TenderRepository tenderRepository;

    @Override
    public SourcePortal portal() {
        return SourcePortal.WORLD_BANK;
    }

    @Override
    public FetchResult discover(Set<String> knownExternalIds) {
        return fetch(knownExternalIds, 2);
    }

    @Override
    public FetchResult fetchAll() {
        return fetch(Set.of(), props.getMaxPages());
    }

    private FetchResult fetch(Set<String> known, int maxPagesPerType) {
        List<Tender> out = new ArrayList<>();
        boolean stoppedEarly = false;
        int pages = 0;
        int duplicates = 0;

        int failedTypes = 0;
        for (String noticeType : props.getNoticeTypes()) {
            try {
                for (int page = 0; page < maxPagesPerType; page++) {
                    JsonNode root = call(noticeType, page * props.getPageSize(), props.getPageSize());
                    pages++;

                    JsonNode notices = root.path("procnotices");
                    if (!notices.isArray() || notices.isEmpty()) {
                        break;
                    }

                    int knownInPage = 0;
                    for (JsonNode n : notices) {
                        String id = n.path("id").asString(null);
                        if (id == null) {
                            continue;
                        }
                        if (known.contains(id)) {
                            knownInPage++;
                            continue;
                        }
                        if (alreadyHaveTheEgpTender(n)) {
                            duplicates++;
                            continue;
                        }
                        out.add(toTender(n));
                    }

                    // An entire page of already-known notices means this type has caught up.
                    if (knownInPage == notices.size()) {
                        stoppedEarly = true;
                        break;
                    }
                    if (notices.size() < props.getPageSize()) {
                        break;
                    }
                }
            } catch (FetchException e) {
                // One type the API is unwell about must not cost us the other three; the
                // next sweep picks it up, and its notices are still there when it does.
                failedTypes++;
                log.warn("World Bank: could not read '{}' this sweep ({}); carrying on with the rest",
                        noticeType, e.getMessage());
            }
        }
        if (failedTypes == props.getNoticeTypes().size()) {
            throw new FetchException("World Bank API unavailable: all "
                    + failedTypes + " notice type(s) failed this sweep");
        }

        log.info("World Bank fetch: {} new notice(s) over {} page(s) of {} type(s){}{}",
                out.size(), pages, props.getNoticeTypes().size(),
                duplicates > 0 ? ", " + duplicates + " already held as e-GP tenders" : "",
                stoppedEarly ? " (stopped early)" : "");
        return new FetchResult(out, pages, 0, stoppedEarly);
    }

    private JsonNode call(String noticeType, int offset, int rows) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(props.getApiUrl())
                .queryParam("format", "json")
                .queryParam("rows", rows)
                .queryParam("os", offset)
                .queryParam("srt", SORT)
                .queryParam("project_ctry_name_exact", props.getCountry());
        if (noticeType != null) {
            uri.queryParam("notice_type_exact", noticeType);
        }
        // encode() because the sort and the notice types carry spaces, then URI.create so
        // RestClient takes it as it is: given a String it would treat it as a template and
        // encode the "%" again, leaving the API to match a notice type containing "%20".
        URI url = URI.create(uri.build().encode().toUriString());

        // The API intermittently returns 500 on otherwise valid requests, so a
        // transient failure must not fail a whole sweep.
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                JsonNode body = restClient.get().uri(url).retrieve().body(JsonNode.class);
                if (body == null) {
                    throw new FetchException("World Bank API returned an empty body");
                }
                return body;
            } catch (RuntimeException e) {
                last = e;
                if (attempt < MAX_ATTEMPTS) {
                    long backoff = BASE_BACKOFF_MS * (1L << (attempt - 1));
                    log.warn("World Bank call failed (attempt {}/{}), retrying in {}ms: {}",
                            attempt, MAX_ATTEMPTS, backoff, e.getMessage());
                    sleep(backoff);
                }
            }
        }
        throw new FetchException(
                "World Bank API call failed after " + MAX_ATTEMPTS + " attempts for " + url
                        + " : " + (last == null ? "unknown" : last.getMessage()), last);
    }

    /**
     * Guards against the silently-ignored-filter trap: a country-filtered query must
     * return strictly fewer notices than the unfiltered corpus.
     *
     * @return the filtered total
     */
    public long assertFilterApplied() {
        long filtered = call(null, 0, 1).path("total").asLong();
        String unfilteredUri = UriComponentsBuilder.fromUriString(props.getApiUrl())
                .queryParam("format", "json").queryParam("rows", 1)
                .build().toUriString();
        JsonNode all = restClient.get().uri(unfilteredUri).retrieve().body(JsonNode.class);
        long unfiltered = all == null ? -1 : all.path("total").asLong();

        if (filtered <= 0 || filtered >= unfiltered) {
            throw new FetchException(
                    "project_ctry_name_exact appears to be ignored: filtered=%d unfiltered=%d"
                            .formatted(filtered, unfiltered));
        }
        log.info("World Bank filter verified: {} notices for {} (of {} total)",
                filtered, props.getCountry(), unfiltered);
        return filtered;
    }

    /** True when this notice is the e-GP tender we already collected directly. */
    private boolean alreadyHaveTheEgpTender(JsonNode n) {
        if (!props.isSkipEgpDuplicates()) {
            return false;
        }
        String egpId = egpTenderId(plainText(text(n, "notice_text")));
        return egpId != null
                && tenderRepository.existsBySourcePortalAndExternalId(SourcePortal.EGP_BANGLADESH, egpId);
    }

    private Tender toTender(JsonNode n) {
        Instant now = Instant.now();
        String plain = plainText(text(n, "notice_text"));

        return Tender.builder()
                .sourcePortal(SourcePortal.WORLD_BANK)
                .externalId(text(n, "id"))
                .referenceNo(truncate(text(n, "bid_reference_no"), 512))
                .title(truncate(text(n, "bid_description"), 2000))
                .description(plain)
                .procurementNature(truncate(text(n, "procurement_group"), 128))
                .procurementType(truncate(text(n, "notice_type"), 64))
                .procurementMethod(truncate(text(n, "procurement_method_name"), 256))
                .organization(truncate(text(n, "project_name"), 512))
                .country(truncate(text(n, "project_ctry_name"), 128))
                .status(truncate(text(n, "notice_status"), 64))
                .publishedAt(parseNoticeDate(text(n, "noticedate")))
                .closingAt(deadline(text(n, "submission_deadline_date"), text(n, "submission_deadline_time"),
                        text(n, "noticedate"), plain))
                .contentHash(HashUtil.sha256(n.toString()))
                .parserVersion(PARSER_VERSION)
                .rawPayload(n.toString())
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build();
    }

    /**
     * When bids are actually due.
     *
     * <p>{@code submission_deadline_date} is the real one, with its own time. It is only
     * trusted when it differs from the publication date: where the two match, the API is
     * repeating the notice date rather than stating a deadline, and treating that as one
     * would mark the tender closed the day it appeared. Failing that, some notices say it
     * in prose. Failing that, unknown -- which the product reads as "no deadline given",
     * not as closed.
     */
    static LocalDateTime deadline(String deadlineDate, String deadlineTime, String noticeDate, String noticeText) {
        LocalDate date = parseIsoDay(deadlineDate);
        LocalDate published = parseNoticeDay(noticeDate);
        if (date != null && !date.equals(published)) {
            LocalTime time = parseTime(deadlineTime);
            // A deadline with no time given is that whole day: close it at the end of it.
            return date.atTime(time == null || time.equals(LocalTime.MIDNIGHT) ? LocalTime.of(23, 59) : time);
        }
        LocalDate prose = parseProseDeadline(noticeText);
        return prose == null ? null : prose.atTime(23, 59);
    }

    /** The e-GP tender id a World Bank notice carries when it is a copy of an e-GP notice. */
    static String egpTenderId(String noticeText) {
        if (noticeText == null) {
            return null;
        }
        Matcher m = EGP_ID.matcher(noticeText);
        return m.find() ? m.group(1) : null;
    }

    static LocalDateTime parseNoticeDate(String raw) {
        LocalDate day = parseNoticeDay(raw);
        return day == null ? null : day.atStartOfDay();
    }

    private static LocalDate parseNoticeDay(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), NOTICE_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    /** "2026-09-22T00:00:00Z" or "2026-09-22" -> the day. */
    private static LocalDate parseIsoDay(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim().substring(0, Math.min(10, raw.trim().length())));
        } catch (Exception e) {
            return null;
        }
    }

    /** "14:30". */
    private static LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate parseProseDeadline(String noticeText) {
        if (noticeText == null) {
            return null;
        }
        Matcher m = PROSE_DEADLINE.matcher(noticeText);
        if (!m.find()) {
            return null;
        }
        try {
            return LocalDate.parse(m.group(1), PROSE_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    private static String plainText(String html) {
        return html == null ? null : Jsoup.parse(html).text();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new FetchException("interrupted while backing off", ie);
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
