package com.bracit.tendersense.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties(prefix = "tendersense.egp")
public class EgpProperties {

    private String baseUrl = "https://www.eprocure.gov.bd";
    private String listingPath = "/resources/common/AllTenders.jsp?h=t";
    private String detailPath = "/resources/common/ViewTender.jsp";

    /** Politeness delay between detail requests. Do not lower: this is a government portal. */
    private long detailDelayMs = 1000;

    private boolean headless = true;

    /** Discovery stops after this many consecutive already-known ids (2 listing pages). */
    private int discoveryStopAfterKnown = 20;

    /** Safety bound so a discovery sweep can never turn into a full crawl by accident. */
    private int discoveryMaxPages = 15;

    private String snapshotDir = "data/snapshots/egp";

    private int pageLoadTimeoutSeconds = 90;
}
