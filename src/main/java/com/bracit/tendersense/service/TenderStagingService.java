package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.FetchResult;
import com.bracit.tendersense.entity.enums.SourcePortal;

import java.util.Set;

/** The first stop for everything fetched: the staging table. */
public interface TenderStagingService {

    /**
     * Queues new and changed tenders. An unchanged tender is only marked as still seen,
     * exactly as before the staging table existed.
     *
     * @return tenders queued
     */
    int stage(FetchResult result);

    /**
     * Queues every stored tender that has not been through the current pipeline -- the
     * one-off backlog run. e-GP pages are re-parsed from their saved snapshots, so parser
     * fixes apply too.
     *
     * @return tenders queued
     */
    int backfill();

    /** Ids on their way in, so discovery does not fetch them a second time. */
    Set<String> queuedExternalIds(SourcePortal portal);
}
