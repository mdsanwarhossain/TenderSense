package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.BracProperties;
import com.bracit.tendersense.dto.FetchResult;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.exception.FetchException;
import com.bracit.tendersense.service.TenderFetchService;
import com.bracit.tendersense.util.HashUtil;
import com.bracit.tendersense.util.PdfText;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BRAC's own procurement, from the public BRAC e-Tender site (tender.brac.net).
 *
 * <p>Two reads, neither behind a login:
 * <ol>
 *   <li>The live tender list: the JSON the site's own "Live Tender List" grid loads.
 *       One request holds the whole list (usually 50-100 tenders).</li>
 *   <li>Each new tender's document: the site has no public detail page (a tender number
 *       opens a supplier login), but it links the tender's PDF on erp.brac.net. Its text
 *       -- items, specifications, quantities, delivery, terms -- becomes the description,
 *       which is what matching and the local model read. Downloaded once, for new
 *       tenders only, one per second.</li>
 * </ol>
 *
 * <p>The list names the staff member who raised each tender, and each PDF ends with that
 * person's name, email and phone. Neither is stored: see {@link #sanitisedPayload} and
 * {@link #cleanDocumentText}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BracTenderFetchServiceImpl implements TenderFetchService {

    static final String PARSER_VERSION = "brac-1";

    /** Column order of a list row's {@code cell} array, from the grid's colNames. */
    static final int ID = 0, TENDER_NO = 1, TITLE = 2, METHOD = 3, ENVELOPE = 4,
            PUBLISHED = 5, LAST_SUBMISSION = 6, OPENING = 7, FINANCIAL_OPENING = 8,
            INITIATED_BY = 9, STATUS = 10, INITIATOR_PIN = 11;

    /** "11/09/2026 03:03 PM", Asia/Dhaka. */
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", Locale.ENGLISH);

    /**
     * The site's method codes. Inferred from the notices themselves: every QM notice is an
     * RFQ, every LTM one an IFT ("sealed quotations ... from bona fide suppliers"), every
     * PM one an RFP. The code stays in brackets; TenderStandardiser strips it.
     */
    private static final Map<String, String> METHODS = Map.of(
            "QM", "Request for quotations (QM)",
            "LTM", "Limited tendering (LTM)",
            "PM", "Request for proposals (PM)");

    /** The notice kind is in the tender number: "BPD/2026/RFQ-2408", "Re-BPD/2026/IFT-2279/v1". */
    private static final Pattern KIND = Pattern.compile("\\b(RFQ|IFT|RFP|REOI|EOI)-", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORKS = Pattern.compile(
            "\\b(construction|renovation|civil works?|refurbishment|repair(?:ing)? (?:and \\w+ )?of (?:the )?(?:building|office|road|school))\\b",
            Pattern.CASE_INSENSITIVE);

    /** Lines of the PDF that are never kept: the author's contact line, print stamps, blanks. */
    private static final List<Pattern> DROPPED_LINES = List.of(
            Pattern.compile("(?i)^\\s*name\\s*:.*designation\\s*:"),
            Pattern.compile("(?i)printing date\\s*&\\s*time"),
            Pattern.compile("(?i)^\\s*page \\d+ of \\d+\\s*$"),
            Pattern.compile("^\\s*[.\\u2026_]{5,}\\s*$"),
            // The letterhead footer repeated on every page.
            Pattern.compile("(?i)^\\s*(BRAC\\s+T:|BRAC CENTRE\\s+F:|75 Mohakhali\\s+E:|Dhaka 1212\\s+W:)"));

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 500;
    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient;
    private final BracProperties props;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ListingPage(int page, int total, int records, List<ListingRow> rows) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ListingRow(List<String> cell) {
    }

    @Override
    public SourcePortal portal() {
        return SourcePortal.BRAC;
    }

    @Override
    public FetchResult discover(Set<String> knownExternalIds) {
        return fetch(knownExternalIds);
    }

    /** Re-reads every live tender, documents included -- a minute or two at one per second. */
    @Override
    public FetchResult fetchAll() {
        return fetch(Set.of());
    }

    private FetchResult fetch(Set<String> known) {
        List<List<String>> rows = new ArrayList<>();
        int pages = 0;
        for (int page = 1; page <= props.getMaxPages(); page++) {
            ListingPage listing = listing(page);
            pages++;
            if (listing.rows() == null || listing.rows().isEmpty()) {
                break;
            }
            listing.rows().stream().map(ListingRow::cell).filter(Objects::nonNull).forEach(rows::add);
            if (page >= listing.total()) {
                break;
            }
        }

        List<Tender> out = new ArrayList<>();
        int documents = 0;
        for (List<String> cell : rows) {
            String id = at(cell, ID);
            if (id == null || known.contains(id)) {
                continue;
            }
            Tender tender = toTender(cell);
            if (documents > 0) {
                sleep(props.getDocumentDelayMs());
            }
            tender.setDescription(document(tender.getReferenceNo()));
            documents++;
            out.add(tender);
        }

        log.info("BRAC fetch: {} live tender(s), {} new, {} document(s) read",
                rows.size(), out.size(), documents);
        return new FetchResult(out, pages, documents, !known.isEmpty() && out.size() < rows.size());
    }

    /** One page of the live list, newest first. */
    ListingPage listing(int page) {
        URI uri = UriComponentsBuilder.fromUriString(props.getBaseUrl() + props.getListPath())
                .queryParam("tenderState", "liveTender")
                .queryParam("hubId", "")
                .queryParam("_search", "false")
                .queryParam("rows", props.getPageSize())
                .queryParam("page", page)
                .queryParam("sidx", "id")
                .queryParam("sord", "desc")
                .build()
                .toUri();

        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String json = restClient.get().uri(uri)
                        .header("User-Agent", BROWSER_UA)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Referer", props.getBaseUrl() + "/tender/liveTenderTemplate")
                        .retrieve()
                        .body(String.class);
                if (json == null || json.isBlank()) {
                    throw new FetchException("BRAC tender list returned an empty body");
                }
                return JSON.readValue(json, ListingPage.class);
            } catch (RuntimeException e) {
                last = e;
                if (attempt < MAX_ATTEMPTS) {
                    long backoff = BASE_BACKOFF_MS * (1L << (attempt - 1));
                    log.warn("BRAC list call failed (attempt {}/{}), retrying in {}ms: {}",
                            attempt, MAX_ATTEMPTS, backoff, e.getMessage());
                    sleep(backoff);
                }
            }
        }
        throw new FetchException("BRAC tender list failed after " + MAX_ATTEMPTS + " attempts: "
                + (last == null ? "unknown" : last.getMessage()), last);
    }

    /**
     * The tender document's text, or null when it cannot be read -- the tender is still
     * stored, from the list's fields alone, rather than lost over one bad file.
     */
    private String document(String tenderNo) {
        if (tenderNo == null || tenderNo.isBlank() || props.getDocumentUrl().isBlank()) {
            return null;
        }
        // Encoded here and passed as a URI, so RestClient does not encode the "%" again.
        URI uri = URI.create(props.getDocumentUrl()
                .replace("{id}", URLEncoder.encode(tenderNo.strip(), StandardCharsets.UTF_8)));
        try {
            byte[] pdf = restClient.get().uri(uri)
                    .header("User-Agent", BROWSER_UA)
                    .retrieve()
                    .body(byte[].class);
            if (!PdfText.looksLikePdf(pdf)) {
                log.warn("BRAC document for {} is not a PDF; storing the list fields only", tenderNo);
                return null;
            }
            if (pdf.length > props.getMaxDocumentBytes()) {
                log.warn("BRAC document for {} is {} bytes, over the limit; skipped", tenderNo, pdf.length);
                return null;
            }
            String text = cleanDocumentText(PdfText.extract(pdf, props.getMaxDocumentPages()),
                    props.getMaxDescriptionChars());
            return text.isBlank() ? null : text;
        } catch (IOException | RuntimeException e) {
            log.warn("BRAC document for {} could not be read: {}", tenderNo, e.getMessage());
            return null;
        }
    }

    static Tender toTender(List<String> cell) {
        Instant now = Instant.now();
        String tenderNo = at(cell, TENDER_NO);
        String title = cleanTitle(at(cell, TITLE), tenderNo);
        String kind = kind(tenderNo);
        String method = at(cell, METHOD);

        // Every list field but the staff member who raised it: those are the ones that
        // change when BRAC amends a tender.
        String contentHash = HashUtil.sha256(String.join("|",
                nullToEmpty(tenderNo), nullToEmpty(title), nullToEmpty(method), nullToEmpty(at(cell, ENVELOPE)),
                nullToEmpty(at(cell, PUBLISHED)), nullToEmpty(at(cell, LAST_SUBMISSION)),
                nullToEmpty(at(cell, OPENING)), nullToEmpty(at(cell, STATUS))));

        return Tender.builder()
                .sourcePortal(SourcePortal.BRAC)
                .externalId(at(cell, ID))
                .referenceNo(truncate(tenderNo, 512))
                .title(truncate(title, 2000))
                .procurementNature(nature(kind, title))
                .procurementType(kind)
                .procurementMethod(method == null ? null : METHODS.getOrDefault(method, method))
                .organization("BRAC")
                .country("Bangladesh")
                .status(truncate(at(cell, STATUS), 64))
                .publishedAt(parseDateTime(at(cell, PUBLISHED)))
                .closingAt(parseDateTime(at(cell, LAST_SUBMISSION)))
                .contentHash(contentHash)
                .parserVersion(PARSER_VERSION)
                .rawPayload(sanitisedPayload(cell))
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build();
    }

    /** The row as named fields, without the staff member's name and staff ID. */
    static String sanitisedPayload(List<String> cell) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("id", at(cell, ID));
        row.put("tenderNo", at(cell, TENDER_NO));
        row.put("title", at(cell, TITLE));
        row.put("method", at(cell, METHOD));
        row.put("envelope", at(cell, ENVELOPE));
        row.put("published", at(cell, PUBLISHED));
        row.put("lastSubmission", at(cell, LAST_SUBMISSION));
        row.put("opening", at(cell, OPENING));
        row.put("financialOpening", at(cell, FINANCIAL_OPENING));
        row.put("status", at(cell, STATUS));
        return JSON.writeValueAsString(row);
    }

    /** "RFQ for the Laptop [BPD/2026/RFQ-2408]" -> "RFQ for the Laptop". */
    static String cleanTitle(String title, String tenderNo) {
        if (title == null) {
            return null;
        }
        String t = tenderNo == null ? title : title.replace("[" + tenderNo + "]", "");
        t = t.strip();
        return t.isEmpty() ? null : t;
    }

    static String kind(String tenderNo) {
        if (tenderNo == null) {
            return null;
        }
        Matcher m = KIND.matcher(tenderNo);
        return m.find() ? m.group(1).toUpperCase(Locale.ROOT) : null;
    }

    /**
     * Goods unless the notice says otherwise: proposals and expressions of interest are
     * consultancy; a title about building work is works. The model's reading refines it.
     */
    static String nature(String kind, String title) {
        if ("RFP".equals(kind) || "EOI".equals(kind) || "REOI".equals(kind)) {
            return "Services";
        }
        if (title != null && WORKS.matcher(title).find()) {
            return "Works";
        }
        return "Goods";
    }

    /**
     * The document's text without its author's contact line, print stamps, dotted form
     * lines or the letterhead footer, with spacing tidied and the length capped.
     */
    static String cleanDocumentText(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean blank = false;
        for (String raw : text.split("\\R")) {
            // Form blanks ("........ Date:") carry nothing; the text either side does.
            String line = raw.replaceAll("[.\\u2026_]{5,}", " ").replaceAll("[ \\t\\u00a0]+", " ").strip();
            if (DROPPED_LINES.stream().anyMatch(p -> p.matcher(line).find())) {
                continue;
            }
            if (line.isEmpty()) {
                if (!blank && !sb.isEmpty()) {
                    sb.append('\n');
                }
                blank = true;
                continue;
            }
            blank = false;
            sb.append(line).append('\n');
        }
        String out = sb.toString().strip();
        return out.length() <= maxChars ? out : out.substring(0, maxChars).strip();
    }

    static LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.strip().toUpperCase(Locale.ROOT), DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    private static String at(List<String> cell, int index) {
        if (cell == null || index >= cell.size()) {
            return null;
        }
        String v = cell.get(index);
        return v == null || v.isBlank() ? null : v.strip();
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
            throw new FetchException("interrupted while waiting between requests", ie);
        }
    }
}
