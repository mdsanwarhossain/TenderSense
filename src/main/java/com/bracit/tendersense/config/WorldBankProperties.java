package com.bracit.tendersense.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

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

    /**
     * The notice types worth collecting, one API call each -- the API takes a single
     * {@code notice_type_exact} and ignores any others, so several types cannot be asked
     * for at once.
     *
     * <p>Two types are left out on purpose. <b>Contract Award</b> is the record of a
     * finished procurement, nothing to bid on, and 5,777 of Bangladesh's 8,935 notices.
     * <b>General Procurement Notice</b> announces a project's procurement plans rather
     * than a call for bids: not one of the 50 most recent carried either a title
     * ({@code bid_description}) or a deadline, so they would land on the shortlist as
     * untitled rows nobody can act on.
     */
    private List<String> noticeTypes = List.of(
            "Invitation for Bids",
            "Request for Expression of Interest",
            "Invitation for Prequalification");

    /**
     * Many World Bank notices for Bangladesh are the e-GP notice again, carrying its
     * "Tender/Proposal ID". Storing both would show the team the same tender twice, so a
     * notice whose e-GP tender we already hold is skipped.
     */
    private boolean skipEgpDuplicates = true;

    private int pageSize = 100;

    /** Per notice type, newest first. */
    private int maxPages = 20;

    /**
     * Public page for one notice; {@code {id}} is the notice id ({@code OP00409684}).
     * Verified: the page's canonical URL is exactly this, with the notice rendered in it.
     */
    private String noticeUrl =
            "https://projects.worldbank.org/en/projects-operations/procurement-detail/{id}";
}
