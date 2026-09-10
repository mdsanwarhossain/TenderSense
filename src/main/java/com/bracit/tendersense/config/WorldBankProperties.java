package com.bracit.tendersense.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties(prefix = "tendersense.worldbank")
public class WorldBankProperties {

    private String apiUrl = "https://search.worldbank.org/api/v2/procnotices";

    /**
     * Filtered via {@code project_ctry_name_exact}. The similarly-named
     * {@code countryname_exact} and {@code cntry_exact} parameters are silently
     * ignored by the API and return the unfiltered corpus.
     */
    private String country = "Bangladesh";

    private int pageSize = 100;

    private int maxPages = 20;

    /**
     * Public page for one notice; {@code {id}} is the notice id ({@code OP00409684}).
     * Verified: the page's canonical URL is exactly this, with the notice rendered in it.
     */
    private String noticeUrl =
            "https://projects.worldbank.org/en/projects-operations/procurement-detail/{id}";
}
