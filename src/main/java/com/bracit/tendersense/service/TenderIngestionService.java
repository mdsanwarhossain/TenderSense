package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.FetchResult;
import com.bracit.tendersense.entity.Tender;

import java.util.List;

public interface TenderIngestionService {

    /**
     * Persists fetched tenders, detecting corrigenda by content hash -- rules only, no
     * staging table. Kept for callers that want a fetch in the tender table at once.
     *
     * @return the tenders that were newly created or revised (unchanged ones are skipped)
     */
    List<Tender> ingest(FetchResult result);

    /** Rules that need no model: sector classification and the standard form. */
    void prepare(Tender tender);

    /**
     * Writes one processed tender -- new, revised, or unchanged with new standard or model
     * fields -- into the tender table.
     */
    PersistOutcome persist(Tender incoming);

    /** Ids already known for a portal, stored or on their way in, for the discovery stop rule. */
    java.util.Set<String> knownExternalIds(com.bracit.tendersense.entity.enums.SourcePortal portal);

    /**
     * @param contentChanged true for a new tender or new content (a corrigendum, a parser
     *                       fix): it needs scoring again
     */
    record PersistOutcome(Tender tender, boolean created, boolean contentChanged) {
    }
}
