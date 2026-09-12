package com.bracit.tendersense.dto;

import com.bracit.tendersense.entity.enums.MatchGrade;
import com.bracit.tendersense.entity.enums.Sector;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.entity.enums.TrackingFilter;

/**
 * The tender list's filters, shared by the list, its summary cards and the dashboard, so a
 * dashboard number always equals what the list shows when you click through to it.
 */
public record ShortlistFilter(MatchGrade grade,
                              SourcePortal source,
                              Sector sector,
                              boolean includeClosed,
                              TrackingFilter tracked,
                              boolean closingSoon) {

    /** Open tenders, every grade -- the list as it first loads. */
    public static final ShortlistFilter OPEN = new ShortlistFilter(null, null, null, false, null, false);

    public ShortlistFilter withGrade(MatchGrade value) {
        return new ShortlistFilter(value, source, sector, includeClosed, tracked, closingSoon);
    }

    public ShortlistFilter withTracked(TrackingFilter value) {
        return new ShortlistFilter(grade, source, sector, includeClosed, value, closingSoon);
    }

    public ShortlistFilter withClosingSoon(boolean value) {
        return new ShortlistFilter(grade, source, sector, includeClosed, tracked, value);
    }

    /**
     * A tender you bid on is usually past its deadline by the time you look it up, so
     * "Submitted" always includes closed tenders -- it would be near-empty otherwise.
     */
    public boolean effectiveIncludeClosed() {
        return includeClosed || tracked == TrackingFilter.SUBMITTED;
    }
}
