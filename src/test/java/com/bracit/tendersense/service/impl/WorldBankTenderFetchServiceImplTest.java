package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.WorldBankProperties;
import com.bracit.tendersense.dto.FetchResult;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.repository.TenderRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Hits the live World Bank API. Skipped when the network is unavailable so the
 * suite still passes offline -- which is the state the demo machine may be in.
 */
class WorldBankTenderFetchServiceImplTest {

    private WorldBankTenderFetchServiceImpl service;
    private TenderRepository tenders;

    @BeforeEach
    void setUp() {
        WorldBankProperties props = new WorldBankProperties();
        props.setPageSize(20);
        props.setMaxPages(1);
        tenders = mock(TenderRepository.class);
        service = new WorldBankTenderFetchServiceImpl(RestClient.builder().build(), props, tenders);
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
    @DisplayName("fetches biddable notices only, newest first, with real deadlines")
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
            assertNotEquals("Contract Award", t.getProcurementType(),
                    "a finished procurement is nothing to bid on and must not be collected");
        }

        long withTitle = result.tenders().stream()
                .filter(t -> t.getTitle() != null && !t.getTitle().isBlank()).count();
        assertTrue(withTitle >= result.count() * 0.8,
                "titles parsed on only " + withTitle + "/" + result.count());

        // The point of reading submission_deadline_date: most notices state one, and it is
        // not simply the day they were published.
        long dated = result.tenders().stream().filter(t -> t.getClosingAt() != null).count();
        assertTrue(dated >= result.count() * 0.5,
                "only " + dated + "/" + result.count() + " carry a deadline -- is the wrong field being read?");
        long sameAsPublished = result.tenders().stream()
                .filter(t -> t.getClosingAt() != null && t.getPublishedAt() != null)
                .filter(t -> t.getClosingAt().toLocalDate().equals(t.getPublishedAt().toLocalDate()))
                .count();
        assertEquals(0, sameAsPublished, "a deadline equal to the publication date is the API repeating itself");

        long open = result.tenders().stream()
                .filter(t -> t.getClosingAt() != null && t.getClosingAt().isAfter(LocalDateTime.now()))
                .count();
        System.out.printf("world bank: %d tenders, %d pages, titles=%d, with deadline=%d, still open=%d%n",
                result.count(), result.pagesScanned(), withTitle, dated, open);
    }

    @Test
    @DisplayName("notices that are the e-GP tender again are left to the e-GP source")
    void skipsTendersAlreadyHeldFromEgp() {
        assumeOnline();
        FetchResult keeping = service.fetchAll();
        Assumptions.assumeFalse(keeping.tenders().isEmpty());

        when(tenders.existsBySourcePortalAndExternalId(any(), anyString())).thenReturn(true);
        FetchResult skipping = service.fetchAll();

        assertTrue(skipping.count() < keeping.count(),
                "with every e-GP tender already held, some notices should have been skipped: "
                        + skipping.count() + " of " + keeping.count());
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
    @DisplayName("the deadline is the submission deadline, never the publication date wearing its name")
    void readsTheRealDeadline() {
        // A stated deadline, with its own time.
        assertEquals(LocalDateTime.of(2026, 9, 22, 12, 0),
                WorldBankTenderFetchServiceImpl.deadline("2026-09-22T00:00:00Z", "12:00", "25-Aug-2026", null));
        // Stated as a day with no time: the end of that day, not the start.
        assertEquals(LocalDateTime.of(2026, 9, 22, 23, 59),
                WorldBankTenderFetchServiceImpl.deadline("2026-09-22T00:00:00Z", "00:00", "25-Aug-2026", null));
        // The API repeating the publication date is not a deadline.
        assertNull(WorldBankTenderFetchServiceImpl.deadline("2026-09-07T00:00:00Z", "00:00", "07-Sep-2026", null),
                "this is what made the whole World Bank corpus look closed");
        // Some notices only say it in words.
        assertEquals(LocalDateTime.of(2026, 5, 7, 23, 59),
                WorldBankTenderFetchServiceImpl.deadline("2026-09-07T00:00:00Z", "00:00", "07-Sep-2026",
                        "For record only, deadline for bid submission was May 7, 2026. View IFT"));
        assertNull(WorldBankTenderFetchServiceImpl.deadline(null, null, "07-Sep-2026", "no date here"));
    }

    @Test
    @DisplayName("the e-GP tender id is read out of a copied e-GP notice")
    void findsTheEgpTenderId() {
        assertEquals("1257451", WorldBankTenderFetchServiceImpl.egpTenderId(
                "View IFT Notice Details Ministry : Finance Tender/Proposal ID : 1257451 Key Information"));
        assertEquals("1303315", WorldBankTenderFetchServiceImpl.egpTenderId("tender / proposal id: 1303315"));
        assertNull(WorldBankTenderFetchServiceImpl.egpTenderId("a World Bank notice of its own"));
        assertNull(WorldBankTenderFetchServiceImpl.egpTenderId(null));
    }

    @Test
    @DisplayName("date formats used by the API")
    void parsesDates() {
        assertEquals(2026, WorldBankTenderFetchServiceImpl.parseNoticeDate("06-Sep-2026").getYear());
        assertNull(WorldBankTenderFetchServiceImpl.parseNoticeDate("garbage"));
        assertNull(WorldBankTenderFetchServiceImpl.parseNoticeDate(""));
    }

    /**
     * Contract awards are finished procurements, and general procurement notices carry
     * neither a title nor a deadline -- both would be rows nobody can act on.
     */
    @Test
    @DisplayName("every collected type is one a company can act on")
    void collectsOnlyBiddableTypes() {
        assertEquals(List.of("Invitation for Bids", "Request for Expression of Interest",
                        "Invitation for Prequalification"),
                new WorldBankProperties().getNoticeTypes());
    }
}
