package com.bracit.tendersense.util;

import com.bracit.tendersense.dto.MatchComparison;
import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.Tender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every case here is a line qwen2.5:3b actually produced for tender 5388, the first real
 * comparison the system wrote. Nothing in this test is invented.
 */
class MatchComparisonValidatorTest {

    private final Tender tender = Tender.builder()
            .title("National Consultant - Digital Transformation Specialist")
            .description("The buyer seeks a national consultant to lead digital transformation "
                    + "of its citizen services, including a five-year maintenance contract "
                    + "and training for government operators.")
            .build();

    private final CapabilityProfile profile = CapabilityProfile.builder()
            .orgName("BracIT")
            .summary("Digital transformation and public service delivery.")
            .services(List.of("Digital transformation advisory and IT strategy consulting",
                    "e-Governance and public service digitisation"))
            .build();

    @Test
    @DisplayName("a match that names the same phrase on both sides is dropped")
    void dropsTheEcho() {
        MatchComparison in = new MatchComparison("A comparison.", List.of(
                "Digital transformation advisory and IT strategy consulting "
                        + "— Digital transformation advisory and IT strategy consulting"),
                List.of());

        MatchComparisonValidator.Result result = MatchComparisonValidator.validate(in, tender, profile);

        assertTrue(result.kept().matches().isEmpty());
        assertTrue(result.dropped().getFirst().contains("same thing twice"));
    }

    @Test
    @DisplayName("a gap written as a comparison keeps its missing half, rather than being lost")
    void salvagesTheTwoSidedGap() {
        // Real output for tender 221: a genuine requirement, written in the wrong shape.
        MatchComparison in = new MatchComparison("A comparison.", List.of(),
                List.of("Minimum annual turnover requirement - No mention of DMTCL's turnover "
                        + "in the company's profile"));

        MatchComparisonValidator.Result result = MatchComparisonValidator.validate(in, tender, profile);

        assertEquals(List.of("Minimum annual turnover requirement"), result.kept().gaps());
        assertTrue(result.dropped().getFirst().contains("rewritten to its missing half"));
    }

    @Test
    @DisplayName("one ask is listed once, however many services the model thought covered it")
    void doesNotRepeatTheSameAsk() {
        // Real output for tender 5388: the same requirement answered three times over.
        String ask = "National Consultant – Digital Transformation Specialist";
        MatchComparison in = new MatchComparison("A comparison.",
                List.of(ask + " - Digital transformation advisory and IT strategy consulting",
                        ask + " - e-Governance service digitisation programme",
                        ask + " - e-Governance and public service digitisation"),
                List.of());

        MatchComparisonValidator.Result result = MatchComparisonValidator.validate(in, tender, profile);

        assertEquals(1, result.kept().matches().size());
        assertEquals(2, result.dropped().size());
        assertTrue(result.dropped().getFirst().contains("repeating an ask"));
    }

    @Test
    @DisplayName("a comparison that runs on is cut to two sentences, never mid-word")
    void trimsTheComparison() {
        MatchComparison in = new MatchComparison(
                "The tender needs a consultant. The company advises on strategy. "
                        + "It also runs e-governance programmes. And financial platforms.",
                List.of(), List.of());

        MatchComparisonValidator.Result result = MatchComparisonValidator.validate(in, tender, profile);

        assertEquals("The tender needs a consultant. The company advises on strategy.",
                result.kept().comparison());
    }

    @Test
    @DisplayName("a gap crediting the company with the work is dropped")
    void dropsTheGapThatIsNotAGap() {
        MatchComparison in = new MatchComparison("A comparison.", List.of(),
                List.of("BracIT already provides e-Governance and public service digitisation"));

        MatchComparisonValidator.Result result = MatchComparisonValidator.validate(in, tender, profile);

        assertTrue(result.kept().gaps().isEmpty());
        assertTrue(result.dropped().getFirst().contains("credits the company"));
    }

    @Test
    @DisplayName("a gap that repeats what was just matched is dropped: nothing is both covered and missing")
    void dropsTheContradiction() {
        // Both lines came back together from qwen2.5:3b for tender 5388.
        MatchComparison in = new MatchComparison("A comparison.",
                List.of("National Consultant - Digital Transformation Specialist "
                        + "— Digital transformation advisory and IT strategy consulting"),
                List.of("Hiring a national consultant"));

        MatchComparisonValidator.Result result = MatchComparisonValidator.validate(in, tender, profile);

        assertEquals(1, result.kept().matches().size());
        assertTrue(result.kept().gaps().isEmpty());
        assertTrue(result.dropped().getFirst().contains("also listed as a match"));
    }

    @Test
    @DisplayName("a sentence the model never finished is not shown")
    void dropsTheUnfinishedSentence() {
        // Real output for tender 221: the model hit its token ceiling mid-word.
        MatchComparison in = new MatchComparison(
                "BracIT delivers software development and managed IT services. "
                        + "Core strengths are management information systems and long-running IT capacity-buildi",
                List.of(), List.of());

        MatchComparisonValidator.Result result = MatchComparisonValidator.validate(in, tender, profile);

        assertEquals("BracIT delivers software development and managed IT services.",
                result.kept().comparison());
    }

    @Test
    @DisplayName("a proper match and a proper gap are kept untouched")
    void keepsTheGoodLines() {
        MatchComparison in = new MatchComparison("The tender needs a consultant. The company advises.",
                List.of("Lead digital transformation of citizen services "
                        + "— digital transformation advisory and IT strategy consulting"),
                List.of("Five-year maintenance contract", "Training for government operators"));

        MatchComparisonValidator.Result result = MatchComparisonValidator.validate(in, tender, profile);

        assertEquals(1, result.kept().matches().size());
        assertEquals(List.of("Five-year maintenance contract", "Training for government operators"),
                result.kept().gaps());
        assertTrue(result.dropped().isEmpty());
    }

    @Test
    @DisplayName("a match about work this tender never mentions is dropped")
    void dropsWhatTheTenderNeverAsked() {
        MatchComparison in = new MatchComparison("A comparison.",
                List.of("Microfinance core banking platform modernisation — core banking work"),
                List.of());

        MatchComparisonValidator.Result result = MatchComparisonValidator.validate(in, tender, profile);

        assertTrue(result.kept().matches().isEmpty());
        assertTrue(result.dropped().getFirst().contains("not from the tender"));
    }

    @Test
    @DisplayName("a hyphen is accepted where the prompt asked for a dash")
    void acceptsEitherDash() {
        MatchComparison in = new MatchComparison("A comparison.",
                List.of("Digital transformation of citizen services - IT strategy consulting"),
                List.of());

        MatchComparisonValidator.Result result = MatchComparisonValidator.validate(in, tender, profile);

        assertEquals(1, result.kept().matches().size());
    }

    @Test
    @DisplayName("repeated lines and blanks are dropped, and the lists are capped")
    void tidiesAndCaps() {
        MatchComparison in = new MatchComparison("  A comparison.  ",
                List.of("Digital transformation of citizen services — IT strategy consulting",
                        "digital transformation of citizen services — it strategy consulting",
                        "   "),
                List.of("Five-year maintenance contract", "Five-year maintenance contract"));

        MatchComparisonValidator.Result result = MatchComparisonValidator.validate(in, tender, profile);

        assertEquals(1, result.kept().matches().size());
        assertEquals(1, result.kept().gaps().size());
        assertEquals("A comparison.", result.kept().comparison());
    }

    @Test
    @DisplayName("a tender with almost no text of its own cannot veto every match")
    void thinTenderKeepsItsMatches() {
        Tender bare = Tender.builder().title("Consultancy").build();

        MatchComparison in = new MatchComparison("A comparison.",
                List.of("Advisory work — IT strategy consulting"), List.of());

        MatchComparisonValidator.Result result = MatchComparisonValidator.validate(in, bare, profile);

        assertEquals(1, result.kept().matches().size());
        assertFalse(result.kept().matches().isEmpty());
    }
}
