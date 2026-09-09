package com.bracit.tendersense.dto;

import com.bracit.tendersense.entity.Tender;

import java.util.List;

/**
 * Outcome of one fetch sweep.
 *
 * @param tenders       parsed tenders, ready to persist
 * @param pagesScanned  listing pages read (0 for API-backed sources)
 * @param detailsFetched expensive per-tender fetches performed
 * @param stoppedEarly  true when the known-id stop rule ended discovery, which is
 *                      the normal outcome of an incremental sweep
 */
public record FetchResult(List<Tender> tenders,
                          int pagesScanned,
                          int detailsFetched,
                          boolean stoppedEarly) {

    public static FetchResult empty() {
        return new FetchResult(List.of(), 0, 0, false);
    }

    public int count() {
        return tenders.size();
    }
}
