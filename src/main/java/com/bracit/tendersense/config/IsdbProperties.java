package com.bracit.tendersense.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter @Setter
@ConfigurationProperties(prefix = "tendersense.isdb")
public class IsdbProperties {

    private String baseUrl = "https://www.isdb.org";

    private String listingPath = "/project-procurement/tenders";

    /** ISO-3166 alpha-2, matches the site's own "loc" filter values (e.g. "BD"). */
    private String countryCode = "BD";

    private int discoveryMaxPages = 2;

    private int reconcileMaxPages = 10;
}
