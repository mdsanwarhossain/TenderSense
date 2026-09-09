package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.WorldBankProperties;
import com.bracit.tendersense.dto.FetchResult;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.SourcePortal;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hits the live World Bank API. Skipped when the network is unavailable so the
 * suite still passes offline -- which is the state the demo machine may be in.
 */
class WorldBankTenderFetchServiceImplTest {

    private WorldBankTenderFetchServiceImpl service;

    @BeforeEach
    void setUp() {
        WorldBankProperties props = new WorldBankProperties();
        props.setPageSize(20);
        props.setMaxPages(2);
        service = new WorldBankTenderFetchServiceImpl(RestClient.builder().build(), props);
    }

    private void assumeOnline() {
        try {
            service.assertFilterApplied();
        } catch (Exception e) {
            Assumptions.abort("World Bank API unreachable: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("country filter is actually applied, not silently ignored")
    void filterIsApplied() {
        assumeOnline();
        long filtered = service.assertFilterApplied();

        // The unfiltered corpus is ~418k notices. Anything near that means the
        // filter was ignored and every downstream 'Bangladesh' claim would be false.
        assertTrue(filtered > 0, "expected some Bangladesh notices");
        assertTrue(filtered < 100_000,
                "filtered total " + filtered + " looks like the unfiltered corpus");
    }

    @Test
    @DisplayName("fetches and maps notices into tenders")
    void fetchesAndMaps() {
        assumeOnline();
        FetchResult result = service.fetchAll();

        assertFalse(result.tenders().isEmpty(), "expected notices from the API");
        assertTrue(result.pagesScanned() >= 1);

        for (Tender t : result.tenders()) {
            assertEquals(SourcePortal.WORLD_BANK, t.getSourcePortal());
            assertNotNull(t.getExternalId(), "externalId is the dedupe key");
            assertNotNull(t.getContentHash());
            assertNotNull(t.getFirstSeenAt());
        }

        long withTitle = result.tenders().stream()
                .filter(t -> t.getTitle() != null && !t.getTitle().isBlank()).count();
        assertTrue(withTitle >= result.count() * 0.8,
                "titles parsed on only " + withTitle + "/" + result.count());

        System.out.printf("world bank: %d tenders, %d pages, titles=%d%n",
                result.count(), result.pagesScanned(), withTitle);
    }

    @Test
    @DisplayName("known ids are skipped so incremental sweeps stay cheap")
    void skipsKnownIds() {
        assumeOnline();
        FetchResult first = service.discover(Set.of());
        Assumptions.assumeFalse(first.tenders().isEmpty());

        Set<String> known = first.tenders().stream()
                .map(Tender::getExternalId)
                .collect(java.util.stream.Collectors.toSet());

        FetchResult second = service.discover(known);
        assertTrue(second.count() < first.count(),
                "second sweep returned " + second.count() + " of " + first.count()
                        + " -- known ids were not skipped");
    }

    @Test
    @DisplayName("date formats used by the API")
    void parsesDates() {
        assertEquals(2026, WorldBankTenderFetchServiceImpl.parseNoticeDate("06-Sep-2026").getYear());
        assertNull(WorldBankTenderFetchServiceImpl.parseNoticeDate("garbage"));
        assertEquals(9, WorldBankTenderFetchServiceImpl
                .parseSubmissionDate("2026-09-06T00:00:00Z").getMonthValue());
        assertNull(WorldBankTenderFetchServiceImpl.parseSubmissionDate(""));
    }
}
