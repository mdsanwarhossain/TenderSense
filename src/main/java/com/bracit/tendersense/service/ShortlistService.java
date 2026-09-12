package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.PageResponse;
import com.bracit.tendersense.dto.ShortlistFilter;
import com.bracit.tendersense.dto.TenderListSummary;
import com.bracit.tendersense.dto.TenderSummaryResponse;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.Sector;
import com.bracit.tendersense.entity.enums.SourcePortal;
import org.springframework.data.domain.Pageable;

/** A company's ranked tender list, and the counts taken from the same query. */
public interface ShortlistService {

    /** Rows ranked by match score, with this company's eligibility and save / submit marks. */
    PageResponse<TenderSummaryResponse> list(Organisation organisation, ShortlistFilter filter, Pageable pageable);

    /** How many rows {@link #list} would return, counted in the database. */
    long count(Organisation organisation, ShortlistFilter filter);

    /**
     * Counts for the tender list's summary cards. They follow only the scope controls
     * (source, sector, Include closed), never the card filters: clicking "Saved" narrows
     * the table; it does not make the S-grade card count only saved tenders.
     */
    TenderListSummary summary(Organisation organisation, SourcePortal source, Sector sector, boolean includeClosed);
}
