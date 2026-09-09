package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.FetchResult;
import com.bracit.tendersense.entity.Tender;

import java.util.List;

public interface TenderIngestionService {

    /**
     * Persists fetched tenders, detecting corrigenda by content hash.
     *
     * @return the tenders that were newly created or revised (unchanged ones are skipped)
     */
    List<Tender> ingest(FetchResult result);

    /** Ids already known for a portal, for the discovery stop rule. */
    java.util.Set<String> knownExternalIds(com.bracit.tendersense.entity.enums.SourcePortal portal);
}
