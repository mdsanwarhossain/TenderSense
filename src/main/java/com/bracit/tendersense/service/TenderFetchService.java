package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.FetchResult;
import com.bracit.tendersense.entity.enums.SourcePortal;

import java.util.Set;

/**
 * One contract, three implementations: live e-GP, live World Bank, and a cached
 * replay of the Day-0 disk snapshot.
 *
 * <p>The cached implementation is the demo safety net -- if the venue network fails
 * on presentation day, switching sources is a profile flag rather than a code change.
 */
public interface TenderFetchService {

    SourcePortal portal();

    /**
     * Cheap incremental sweep, newest first, stopping once enough consecutive
     * already-known ids have been seen.
     *
     * @param knownExternalIds ids already persisted for this portal
     */
    FetchResult discover(Set<String> knownExternalIds);

    /** Full reconciliation crawl. Expensive -- nightly only. */
    FetchResult fetchAll();
}
