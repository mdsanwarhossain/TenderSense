package com.bracit.tendersense.util;

import com.bracit.tendersense.dto.TenderEnrichment;
import com.bracit.tendersense.entity.Tender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnrichmentValidatorTest {

    private static final String LONG_TITLE = "ADP-2024-2025-W-02 (A), Palisading of the Road at Kaurikhara "
            + "Speed Boat Ghate under Rangabali Upazila, Dist: Patuakhali (memo No-1387 Date:02 July,25 "
            + "Sl No- Sch-02) under the Upazila Parishad development fund for the current fiscal year";

    private static Tender tender(String title, String description, String eligibility) {
        return Tender.builder().title(title).description(description).eligibilityText(eligibility).build();
    }

    private static TenderEnrichment answer(String shortTitle) {
        return new TenderEnrichment(shortTitle, "The buyer wants the road bank protected with palisading.",
                List.of("Palisading along the road"), "Patuakhali", null, null, List.of());
    }

    @Test
    @DisplayName("a short title that invents work the tender does not mention is rejected")
    void rejectsInventedWords() {
        var r = EnrichmentValidator.validate(
                answer("Palisading and Road Construction at Speed Boat Ghate, Patuakhali"),
                tender(LONG_TITLE, "", ""), 150);
        assertNull(r.kept().shortTitle());
        assertTrue(r.dropped().stream().anyMatch(d -> d.contains("construction")));
        assertNotNull(r.kept().summary(), "the rest of the answer is kept");
    }

    @Test
    @DisplayName("a short title made of the tender's own words is kept")
    void keepsFaithfulTitle() {
        var r = EnrichmentValidator.validate(answer("Palisading of the Road at Speed Boat Ghate, Patuakhali"),
                tender(LONG_TITLE, "", ""), 150);
        assertEquals("Palisading of the Road at Speed Boat Ghate, Patuakhali", r.kept().shortTitle());
    }

    @Test
    @DisplayName("no short title is stored for a title that was already short")
    void noShortTitleForShortTitles() {
        var r = EnrichmentValidator.validate(answer("Supply of desktop computers"),
                tender("Supply of desktop computers for the Ministry", "", ""), 150);
        assertNull(r.kept().shortTitle());
    }

    @Test
    @DisplayName("a turnover figure must be one the notice states; lakh and crore count")
    void turnoverMustBeStated() {
        String elig = "The tenderer shall have a minimum average annual turnover of Tk. 2.5 crore in the last 5 years.";
        var stated = new TenderEnrichment(null, null, List.of(), null, new BigDecimal("25000000"), 5, List.of());
        var invented = new TenderEnrichment(null, null, List.of(), null, new BigDecimal("30000000"), 7, List.of());

        var ok = EnrichmentValidator.validate(stated, tender("Supply of computers", "", elig), 150);
        assertEquals(0, new BigDecimal("25000000").compareTo(ok.kept().minTurnoverBdt()));
        assertEquals(5, ok.kept().minExperienceYears());

        var bad = EnrichmentValidator.validate(invented, tender("Supply of computers", "", elig), 150);
        assertNull(bad.kept().minTurnoverBdt());
        assertNull(bad.kept().minExperienceYears());
    }

    @Test
    @DisplayName("a deliverable that is only in e-GP's category list is dropped")
    void categoryListIsNotADeliverable() {
        String cpv = "Machinery and equipment; Tractors; Watches and clocks; Hospital furniture";
        Tender t = Tender.builder().title("Procurement of hospital furniture for Khulna General Hospital")
                .description("Supply of hospital beds and cabinets.\n\n" + cpv).cpvRaw(cpv).build();
        var in = new TenderEnrichment(null, "The hospital wants furniture supplied.",
                List.of("Hospital beds", "Tractors", "Watches and clocks", "Hospital furniture"),
                null, null, null, List.of());
        var r = EnrichmentValidator.validate(in, t, 150);
        assertEquals(List.of("Hospital beds", "Hospital furniture"), r.kept().deliverables(),
                "'hospital furniture' is also in the title, so it stays");
        assertFalse(EnrichmentPrompt.user(t).contains("Tractors"), "the model never sees the category list");
    }

    @Test
    @DisplayName("a reference code is not a location")
    void referenceCodeIsNotALocation() {
        var in = new TenderEnrichment(null, null, List.of(), "Faridpur, 26-27/11 Package 1111111111", null, null, List.of());
        var r = EnrichmentValidator.validate(in, tender("faridpur/uhc/diet/26-27/11 Supply of Dietary Supplement", "", ""), 150);
        assertEquals("Faridpur", r.kept().location());
    }

    @Test
    @DisplayName("certifications and locations are kept only when the notice names them")
    void certificationsAndLocation() {
        var in = new TenderEnrichment(null, null, List.of(), "Dhaka, Sylhet", null, null,
                List.of("ISO 9001", "CMMI Level 3"));
        var r = EnrichmentValidator.validate(in,
                tender("Software for the Dhaka office", "", "Bidder must hold ISO 9001:2015 certification."), 150);
        assertEquals(List.of("ISO 9001"), r.kept().certifications());
        assertEquals("Dhaka", r.kept().location());
    }
}
