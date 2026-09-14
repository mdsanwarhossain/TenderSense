package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.ProcessingStatusResponse;

/** Moves tenders from the staging table into the tender table, a few at a time. */
public interface TenderProcessingService {

    /**
     * One batch: closed tenders in bulk without the model, then a handful of open ones
     * through it. Does not score -- see {@link #scorePersisted()}.
     *
     * @param useModel false processes by rules only
     * @return rows processed
     */
    int processBatch(boolean useModel);

    /** Processes every waiting row by rules only -- the path when the model is off. */
    int drainWithoutModel();

    /**
     * Scores, for every company, the tenders written since the last call. The caller holds
     * the pipeline lock: scoring must never race a rescore.
     *
     * @return match rows written
     */
    int scorePersisted();

    ProcessingStatusResponse status();
}
