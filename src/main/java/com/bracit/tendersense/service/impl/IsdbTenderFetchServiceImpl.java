package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.IsdbProperties;
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
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Islamic Development Bank project procurement notices, filtered to Bangladesh.
 *
 * <p>Plain server-rendered Drupal views listing -- no API, no login, filtered with
 * ordinary query parameters ({@code loc}, {@code status}, {@code page}). One
 * {@code article.type-tender} per row, which is everything this needs: unlike e-GP,
 * there is no separate detail page richer than the listing to justify a second fetch
 * per tender.
 *
 * <p>{@code status} is a listing filter, not a per-tender field the site exposes twice
 * consistently -- discovery reads only {@code active}, and reconcile also sweeps
 * {@code closed} so a status flip (active -> closed) still surfaces as a revision.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IsdbTenderFetchServiceImpl implements TenderFetchService {

    private static final List<String> STATUSES = List.of("active", "closed");

    /** e.g. "24 August 2025". */
    private static final DateTimeFormatter CLOSE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 500;

    private final RestClient restClient;
    private final IsdbProperties props;

    @Override
    public SourcePortal portal() {
        return SourcePortal.ISDB;
    }

    @Override
    public FetchResult discover(Set<String> knownExternalIds) {
        return fetch(knownExternalIds, List.of("active"), props.getDiscoveryMaxPages());
    }

    @Override
    public FetchResult fetchAll() {
        return fetch(Set.of(), STATUSES, props.getReconcileMaxPages());
    }

    private FetchResult fetch(Set<String> known, List<String> statuses, int maxPagesPerStatus) {
        List<Tender> out = new ArrayList<>();
        boolean stoppedEarly = false;
        int pages = 0;

        for (String status : statuses) {
            for (int page = 0; page < maxPagesPerStatus; page++) {
                Document doc = call(status, page);
                pages++;

                Elements rows = doc.select("article.type-tender");
                if (rows.isEmpty()) {
                    break;
                }

                int knownInPage = 0;
                for (Element row : rows) {
                    String id = row.attr("data-nid");
                    if (id.isBlank()) {
                        continue;
                    }
                    if (known.contains(id)) {
                        knownInPage++;
                        continue;
                    }
                    out.add(toTender(row, id));
                }

                // A whole page of already-known ids means this status is caught up --
                // move on to the next status rather than reading further empty pages.
                if (knownInPage == rows.size()) {
                    stoppedEarly = true;
                    break;
                }
            }
        }

        log.info("IsDB fetch: {} new tender(s) over {} page(s){}",
                out.size(), pages, stoppedEarly ? " (stopped early)" : "");
        return new FetchResult(out, pages, 0, stoppedEarly);
    }

    private Document call(String status, int page) {
        String uri = UriComponentsBuilder.fromUriString(props.getBaseUrl() + props.getListingPath())
                .queryParam("loc", props.getCountryCode())
                .queryParam("status", status)
                .queryParam("page", page)
                .build()
                .toUriString();

        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String html = restClient.get().uri(uri).retrieve().body(String.class);
                if (html == null) {
                    throw new FetchException("IsDB listing returned an empty body");
                }
                return Jsoup.parse(html);
            } catch (RuntimeException e) {
                last = e;
                if (attempt < MAX_ATTEMPTS) {
                    long backoff = BASE_BACKOFF_MS * (1L << (attempt - 1));
                    log.warn("IsDB call failed (attempt {}/{}), retrying in {}ms: {}",
                            attempt, MAX_ATTEMPTS, backoff, e.getMessage());
                    sleep(backoff);
                }
            }
        }
        throw new FetchException("IsDB listing fetch failed after " + MAX_ATTEMPTS + " attempts: "
                + (last == null ? "unknown" : last.getMessage()), last);
    }

    private Tender toTender(Element row, String id) {
        Instant now = Instant.now();

        String title = text(row, ".field-title h2 a");
        String status = text(row, ".field--name-field-tender-status");
        String type = text(row, ".field--name-field-tender-type");
        String country = text(row, ".field--name-field-world-country");
        String closeDateRaw = text(row, ".field--name-field-close-date time");

        String contentHash = HashUtil.sha256(String.join("|",
                nullToEmpty(title), nullToEmpty(status), nullToEmpty(type),
                nullToEmpty(country), nullToEmpty(closeDateRaw)));

        return Tender.builder()
                .sourcePortal(SourcePortal.ISDB)
                .externalId(id)
                .title(truncate(title, 2000))
                .procurementType(truncate(type, 64))
                .organization("Islamic Development Bank (IsDB)")
                .country(truncate(country, 128))
                .status(truncate(status, 64))
                .closingAt(parseCloseDate(closeDateRaw))
                .contentHash(contentHash)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build();
    }

    static LocalDateTime parseCloseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), CLOSE_DATE_FORMAT).atStartOfDay();
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
