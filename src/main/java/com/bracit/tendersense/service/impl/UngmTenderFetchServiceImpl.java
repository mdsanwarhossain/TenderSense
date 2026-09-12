package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.UngmProperties;
import com.bracit.tendersense.dto.FetchResult;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.exception.FetchException;
import com.bracit.tendersense.service.TenderFetchService;
import com.bracit.tendersense.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * UN Global Marketplace procurement notices, filtered to Bangladesh.
 *
 * <p>No public JSON API: {@code /Public/Notice/Search} is the same endpoint the site's
 * own notice grid calls, and it returns a server-rendered HTML fragment rather than
 * JSON -- one {@code div.tableRow.dataRow} per notice. No login is required to reach
 * it. The country filter is UNGM's own numeric id, not an ISO code, read off the
 * public site's country dropdown ({@link UngmProperties#getCountryId()}).
 *
 * <p>Only the fields the listing itself carries are stored -- title, reference,
 * agency, notice type, deadline, published date, beneficiary country. Fetching each
 * notice's own detail page for a fuller description would multiply requests for
 * marginal signal the title mostly already carries, so this deliberately does not,
 * the same trade-off {@code WorldBankTenderFetchServiceImpl} makes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UngmTenderFetchServiceImpl implements TenderFetchService {

    private static final DateTimeFormatter DEADLINE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter PUBLISHED_FORMAT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 500;

    /**
     * UNGM's edge WAF rejects a cold POST with no session and no browser-shaped
     * headers as bot traffic -- a plain {@code RestClient} call with no
     * {@code User-Agent} gets a 403 "Access is denied" before the request ever
     * reaches the notice search itself. A real browser always has a session cookie
     * from having loaded the page first, so this fetches one the same way.
     */
    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final RestClient restClient;
    private final UngmProperties props;

    @Override
    public SourcePortal portal() {
        return SourcePortal.UNGM;
    }

    @Override
    public FetchResult discover(Set<String> knownExternalIds) {
        return fetch(knownExternalIds, props.getDiscoveryMaxPages());
    }

    @Override
    public FetchResult fetchAll() {
        return fetch(Set.of(), props.getReconcileMaxPages());
    }

    private FetchResult fetch(Set<String> known, int maxPages) {
        List<Tender> out = new ArrayList<>();
        boolean stoppedEarly = false;
        int pages = 0;

        // One session for the whole sweep -- the WAF ties the cookie to the client,
        // not to the individual page, so there is nothing to gain re-fetching it per page.
        String cookie = sessionCookie();

        for (int page = 0; page < maxPages; page++) {
            Document doc = call(page, cookie);
            pages++;

            Elements rows = doc.select("div.tableRow.dataRow");
            if (rows.isEmpty()) {
                break;
            }

            int knownInPage = 0;
            for (Element row : rows) {
                String id = row.attr("data-noticeid");
                if (id.isBlank()) {
                    continue;
                }
                if (known.contains(id)) {
                    knownInPage++;
                    continue;
                }
                out.add(toTender(row, id));
            }

            if (knownInPage == rows.size()) {
                stoppedEarly = true;
                break;
            }
            if (rows.size() < props.getPageSize()) {
                break;
            }
        }

        log.info("UNGM fetch: {} new notice(s) over {} page(s){}",
                out.size(), pages, stoppedEarly ? " (stopped early)" : "");
        return new FetchResult(out, pages, 0, stoppedEarly);
    }

    /** GETs the public notice page purely to collect the session cookie it sets. */
    private String sessionCookie() {
        try {
            ResponseEntity<Void> resp = restClient.get()
                    .uri(props.getBaseUrl() + "/Public/Notice")
                    .header("User-Agent", BROWSER_UA)
                    .retrieve()
                    .toBodilessEntity();
            List<String> setCookies = resp.getHeaders().get(HttpHeaders.SET_COOKIE);
            if (setCookies == null || setCookies.isEmpty()) {
                return null;
            }
            return setCookies.stream()
                    .map(c -> c.split(";", 2)[0])
                    .collect(Collectors.joining("; "));
        } catch (RuntimeException e) {
            log.warn("UNGM session fetch failed, continuing without a cookie: {}", e.getMessage());
            return null;
        }
    }

    /** Newest published first, so discovery's known-id stop rule sees new ids first. */
    private Document call(int pageIndex, String cookie) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("PageIndex", pageIndex);
        body.put("PageSize", props.getPageSize());
        body.put("Title", "");
        body.put("Description", "");
        body.put("Reference", "");
        body.put("PublishedFrom", "");
        body.put("PublishedTo", "");
        body.put("DeadlineFrom", "");
        body.put("DeadlineTo", "");
        body.put("Countries", List.of(props.getCountryId()));
        body.put("Agencies", List.of());
        body.put("UNSPSCs", List.of());
        body.put("NoticeTypes", List.of());
        body.put("SortField", "DatePublished");
        body.put("SortAscending", false);
        body.put("isPicker", false);
        body.put("IsSustainable", false);
        body.put("IsActive", true);
        body.put("NoticeDisplayType", "");
        body.put("NoticeSearchTotalLabelId", "noticeSearchTotal");
        body.put("TypeOfCompetitions", List.of());

        String uri = props.getBaseUrl() + props.getSearchPath();
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                RestClient.RequestBodySpec req = restClient.post().uri(uri)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("User-Agent", BROWSER_UA)
                        .header("Referer", props.getBaseUrl() + "/Public/Notice")
                        .contentType(MediaType.APPLICATION_JSON);
                if (cookie != null) {
                    req = req.header("Cookie", cookie);
                }
                String html = req.body(body).retrieve().body(String.class);
                if (html == null) {
                    throw new FetchException("UNGM search returned an empty body");
                }
                return Jsoup.parseBodyFragment(html);
            } catch (RuntimeException e) {
                last = e;
                if (attempt < MAX_ATTEMPTS) {
                    long backoff = BASE_BACKOFF_MS * (1L << (attempt - 1));
                    log.warn("UNGM call failed (attempt {}/{}), retrying in {}ms: {}",
                            attempt, MAX_ATTEMPTS, backoff, e.getMessage());
                    sleep(backoff);
                }
            }
        }
        throw new FetchException("UNGM search failed after " + MAX_ATTEMPTS + " attempts: "
                + (last == null ? "unknown" : last.getMessage()), last);
    }

    private Tender toTender(Element row, String id) {
        Instant now = Instant.now();

        String title = text(row, ".resultTitle .ungm-title");
        String deadlineRaw = text(row, ".resultInfo1.deadline span");
        String reference = text(row, ".resultInfo1[data-description=Reference] span");
        String agency = text(row, ".resultAgency span");

        // The listing has no class or data attribute on the "published date" and
        // "beneficiary country" cells -- they are only identifiable by column order.
        Elements cells = row.select("> div.tableCell");
        String published = cells.size() > 3 ? cells.get(3).text().trim() : null;
        String noticeType = cells.size() > 5 ? cells.get(5).text().trim() : null;
        String country = cells.size() > 7 ? cells.get(7).text().trim() : null;

        // The deadline cell also carries a "days remaining" fraction that UNGM recomputes
        // on every request -- hashing raw row HTML would flag every notice as revised on
        // every single fetch. Hashing the extracted fields instead is immune to that.
        String contentHash = HashUtil.sha256(String.join("|",
                nullToEmpty(title), nullToEmpty(reference), nullToEmpty(deadlineRaw),
                nullToEmpty(agency), nullToEmpty(noticeType), nullToEmpty(country)));

        return Tender.builder()
                .sourcePortal(SourcePortal.UNGM)
                .externalId(id)
                .referenceNo(truncate(reference, 512))
                .title(truncate(title, 2000))
                .procurementType(truncate(noticeType, 64))
                .procurementMethod(truncate(noticeType, 256))
                .organization(truncate(agency, 512))
                .country(truncate(country, 128))
                .publishedAt(parsePublished(published))
                .closingAt(parseDeadline(deadlineRaw))
                .contentHash(contentHash)
                .rawPayload(row.outerHtml())
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build();
    }

    static LocalDateTime parseDeadline(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // "10-Sep-2026 06:30\n            (GMT -4.00)" -> "10-Sep-2026 06:30"
        String cleaned = raw.replaceAll("\\(GMT[^)]*\\)", "").trim().replaceAll("\\s+", " ");
        try {
            return LocalDateTime.parse(cleaned, DEADLINE_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }

    static LocalDateTime parsePublished(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), PUBLISHED_FORMAT).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(Element row, String selector) {
        Element el = row.selectFirst(selector);
        if (el == null) {
            return null;
        }
        String t = el.text().trim();
        return t.isEmpty() ? null : t;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new FetchException("interrupted while backing off", ie);
        }
    }
}
