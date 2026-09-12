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

    /**
     * Public page for one tender, {@code {id}} being the stored external id. Blank on
     * purpose: /node/{nid} returns 404 on isdb.org, and no ISDB tender is stored yet to
     * verify a working pattern against. With no template the shortlist hides the link
     * rather than offering a broken one. Set it once a real ISDB tender confirms the URL.
     */
    private String noticeUrl = "";

    private int discoveryMaxPages = 2;

    private int reconcileMaxPages = 10;
}
