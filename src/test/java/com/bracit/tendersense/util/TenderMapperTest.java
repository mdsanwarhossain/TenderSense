package com.bracit.tendersense.util;

import com.bracit.tendersense.config.EgpProperties;
import com.bracit.tendersense.config.IsdbProperties;
import com.bracit.tendersense.config.UngmProperties;
import com.bracit.tendersense.config.WorldBankProperties;
import com.bracit.tendersense.dto.TenderSummaryResponse;
import com.bracit.tendersense.dto.TrackingState;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.SourcePortal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TenderMapperTest {

    private final TenderMapper mapper = new TenderMapper(
            new EgpProperties(), new WorldBankProperties(), new UngmProperties(), new IsdbProperties());

    private static Tender tender(SourcePortal portal, String externalId) {
        return Tender.builder().id(1L).sourcePortal(portal).externalId(externalId).title("x").build();
    }

    @Test
    @DisplayName("each portal's link is its real public page -- each pattern checked against the live site")
    void portalLinks() {
        assertEquals("https://www.eprocure.gov.bd/resources/common/ViewTender.jsp?id=1320521&h=t",
                mapper.sourceUrl(tender(SourcePortal.EGP_BANGLADESH, "1320521")));
        assertEquals("https://projects.worldbank.org/en/projects-operations/procurement-detail/OP00409684",
                mapper.sourceUrl(tender(SourcePortal.WORLD_BANK, "OP00409684")));
        assertEquals("https://www.ungm.org/Public/Notice/1001",
                mapper.sourceUrl(tender(SourcePortal.UNGM, "1001")));
        assertNull(mapper.sourceUrl(tender(SourcePortal.ISDB, "1167")),
                "no verified ISDB pattern yet: hide the link rather than guess one");
    }

    @Test
    @DisplayName("no id means no link, rather than a link to nowhere")
    void noId() {
        assertNull(mapper.sourceUrl(tender(SourcePortal.EGP_BANGLADESH, null)));
        assertNull(mapper.sourceUrl(tender(SourcePortal.WORLD_BANK, "  ")));
    }

    @Test
    @DisplayName("an id is encoded, so it cannot break or redirect the URL")
    void encoded() {
        assertEquals("https://www.ungm.org/Public/Notice/a+b%2F..%3Fx%3D1",
                mapper.sourceUrl(tender(SourcePortal.UNGM, "a b/..?x=1")));
    }

    @Test
    @DisplayName("tracking state reaches the shortlist row; none means not saved, not submitted")
    void tracking() {
        Tender t = tender(SourcePortal.EGP_BANGLADESH, "1");
        Instant at = Instant.parse("2026-09-12T04:00:00Z");

        TenderSummaryResponse untracked = mapper.toSummary(t, null, null);
        assertFalse(untracked.wishlisted());
        assertFalse(untracked.submitted());
        assertNull(untracked.submittedAt());

        TenderSummaryResponse tracked = mapper.toSummary(t, null, null, TrackingState.of(1L, at, at));
        assertTrue(tracked.wishlisted());
        assertTrue(tracked.submitted());
        assertEquals(at, tracked.submittedAt());
    }
}
