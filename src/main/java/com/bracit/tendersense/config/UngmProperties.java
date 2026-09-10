package com.bracit.tendersense.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties(prefix = "tendersense.ungm")
public class UngmProperties {

    private String baseUrl = "https://www.ungm.org";

    private String searchPath = "/Public/Notice/Search";

    /**
     * UNGM's own numeric id for the beneficiary-country filter -- not an ISO code.
     * 2309 is Bangladesh, read off the country <select> on the public notice page;
     * there is no published lookup API for it.
     */
    private int countryId = 2309;

    private int pageSize = 50;

    /** Incremental sweep: newest first, stop once a page is entirely already-known ids. */
    private int discoveryMaxPages = 2;

    /** Full reconcile: enough pages to see the whole Bangladesh-filtered corpus. */
    private int reconcileMaxPages = 10;
}
