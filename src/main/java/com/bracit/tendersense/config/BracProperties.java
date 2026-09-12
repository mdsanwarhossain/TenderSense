package com.bracit.tendersense.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties(prefix = "tendersense.brac")
public class BracProperties {

    private String baseUrl = "https://tender.brac.net";
    /**
     * The JSON the public "Live Tender List" grid loads (jqGrid). No login needed; the
     * same endpoint the page itself calls.
     */
    private String listPath = "/tender/loadTenderExternalList";
    /** Rows per request. The live list is usually well under 100, so one page holds it. */
    private int pageSize = 200;
    private int maxPages = 5;
    /**
     * A tender's own document, by tender number ({@code {id}}). The public site has no
     * detail page -- clicking a tender asks for a supplier login -- but links this PDF.
     */
    private String documentUrl = "https://erp.brac.net/procUtil/viewRFQDocument?tenderNo={id}";
    /** Politeness between document downloads, as for e-GP detail pages. */
    private long documentDelayMs = 1000;
    /** Larger documents are skipped: the text is what matters, and scans carry none. */
    private int maxDocumentBytes = 15 * 1024 * 1024;
    private int maxDocumentPages = 20;
    /** Enough for the item list, specification and terms; the matcher reads less anyway. */
    private int maxDescriptionChars = 12_000;
}
