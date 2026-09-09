package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.WorldBankProperties;
import com.bracit.tendersense.dto.FetchResult;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.exception.FetchException;
import com.bracit.tendersense.service.TenderFetchService;
import com.bracit.tendersense.util.HashUtil;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * World Bank procurement notices. No scraping: this is a public JSON API.
 *
 * <p>Filtering uses {@code project_ctry_name_exact}. Two similarly-named parameters,
 * {@code countryname_exact} and {@code cntry_exact}, are accepted by the API and then
 * silently ignored, returning the full ~418k corpus -- a filter that looks like it
 * works while doing nothing. {@link #assertFilterApplied} exists to catch exactly that.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorldBankTenderFetchServiceImpl implements TenderFetchService {

    private static final DateTimeFormatter NOTICE_DATE =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 500;

    private final RestClient restClient;
    private final WorldBankProperties props;

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

    private FetchResult fetch(Set<String> known, int maxPages) {
        List<Tender> out = new ArrayList<>();
        boolean stoppedEarly = false;
        int pages = 0;

        for (int page = 0; page < maxPages; page++) {
            JsonNode root = call(page * props.getPageSize(), props.getPageSize());
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
                out.add(toTender(n));
            }

            // An entire page of already-known notices means we have caught up.
            if (knownInPage == notices.size()) {
                stoppedEarly = true;
                break;
            }
            if (notices.size() < props.getPageSize()) {
                break;
            }
        }

        log.info("World Bank fetch: {} new notices over {} page(s){}",
                out.size(), pages, stoppedEarly ? " (stopped early)" : "");
        return new FetchResult(out, pages, 0, stoppedEarly);
    }

    private JsonNode call(int offset, int rows) {
        String uri = UriComponentsBuilder.fromUriString(props.getApiUrl())
                .queryParam("format", "json")
                .queryParam("rows", rows)
                .queryParam("os", offset)
                .queryParam("project_ctry_name_exact", props.getCountry())
                .build()
                .toUriString();
        // The API intermittently returns 500 on otherwise valid requests, so a
        // transient failure must not fail a whole sweep.
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                JsonNode body = restClient.get().uri(uri).retrieve().body(JsonNode.class);
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
                "World Bank API call failed after " + MAX_ATTEMPTS + " attempts for " + uri
                        + " : " + (last == null ? "unknown" : last.getMessage()), last);
    }

    /**
     * Guards against the silently-ignored-filter trap: a country-filtered query must
     * return strictly fewer notices than the unfiltered corpus.
     *
     * @return the filtered total
     */
    public long assertFilterApplied() {
        long filtered = call(0, 1).path("total").asLong();
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

    private Tender toTender(JsonNode n) {
        Instant now = Instant.now();
        String noticeText = text(n, "notice_text");
        String plain = noticeText == null ? null : Jsoup.parse(noticeText).text();

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
                .closingAt(parseSubmissionDate(text(n, "submission_date")))
                .contentHash(HashUtil.sha256(n.toString()))
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build();
    }

    static LocalDateTime parseNoticeDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), NOTICE_DATE).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    static LocalDateTime parseSubmissionDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(raw.trim()), ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
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
