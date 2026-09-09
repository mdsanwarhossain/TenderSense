package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.RuleOutcome;
import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.Certification;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.util.MoneyTextParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The eligibility path must never call a model, and must never turn "the tender did
 * not say" into a pass. These tests pin both properties.
 */
class EligibilityRulesTest {

    private CapabilityProfile profile(String... certCodes) {
        CapabilityProfile p = CapabilityProfile.builder()
                .orgName("BracIT")
                .annualTurnoverBdt(new BigDecimal("1200000000"))
                .services(List.of("Custom software development"))
                .geographies(new java.util.ArrayList<>(List.of("Bangladesh", "South Asia", "International")))
                .build();
        for (String code : certCodes) {
            p.getCertifications().add(Certification.builder()
                    .profile(p).code(code).name(code)
                    .validUntil(LocalDate.now().plusYears(1)).build());
        }
        return p;
    }

    private Tender tender(String eligibilityText) {
        return Tender.builder()
                .sourcePortal(SourcePortal.EGP_BANGLADESH)
                .externalId("t1")
                .country("Bangladesh")
                .eligibilityText(eligibilityText)
                .build();
    }

    @Nested
    class Turnover {
        private final TurnoverRule rule = new TurnoverRule();

        @Test
        @DisplayName("passes when our turnover clears the stated threshold")
        void passes() {
            RuleOutcome o = rule.evaluate(
                    tender("The bidder must have an annual turnover of BDT 50,000,000."), profile());
            assertEquals(RuleOutcome.Result.PASS, o.result());
        }

        @Test
        @DisplayName("fails with the shortfall quantified, not just 'ineligible'")
        void failsWithShortfall() {
            RuleOutcome o = rule.evaluate(
                    tender("Minimum average annual turnover of BDT 5,000,000,000 is required."),
                    profile());
            assertEquals(RuleOutcome.Result.FAIL, o.result());
            assertTrue(o.message().contains("3,800,000,000"),
                    "expected the gap to be quantified, got: " + o.message());
        }

        @Test
        @DisplayName("boilerplate eligibility yields UNKNOWN, never a pass")
        void boilerplateIsUnknown() {
            // Roughly half the corpus looks exactly like this.
            assertEquals(RuleOutcome.Result.UNKNOWN,
                    rule.evaluate(tender(""), profile()).result());
            assertEquals(RuleOutcome.Result.NOT_APPLICABLE,
                    rule.evaluate(tender("As per TDS"), profile()).result());
        }

        @Test
        @DisplayName("figures unrelated to turnover are not mistaken for the threshold")
        void ignoresUnrelatedAmounts() {
            // Only the turnover sentence is parsed: a large security deposit elsewhere
            // in the text must not become the threshold.
            RuleOutcome o = rule.evaluate(tender(
                    "Tender security of BDT 9,000,000,000 must be submitted. "
                            + "The bidder shall have an annual turnover of BDT 40,000,000."),
                    profile());
            assertEquals(RuleOutcome.Result.PASS, o.result(),
                    "the security deposit was read as the turnover requirement");
        }
    }

    @Nested
    class Certifications {
        private final CertificationRule rule = new CertificationRule();

        @Test
        @DisplayName("recognises spelling variants of the same certification")
        void matchesVariants() {
            RuleOutcome o = rule.evaluate(
                    tender("Bidder must hold ISO/IEC 27001 certification."),
                    profile("ISO27001"));
            assertEquals(RuleOutcome.Result.PASS, o.result(),
                    "ISO/IEC 27001 should match the held ISO27001");
        }

        @Test
        @DisplayName("names what is missing so the gap is actionable")
        void namesMissingCertification() {
            RuleOutcome o = rule.evaluate(
                    tender("Bidder must hold ISO 27001 and ISO 9001."),
                    profile("ISO9001"));
            assertEquals(RuleOutcome.Result.FAIL, o.result());
            assertTrue(o.message().contains("ISO27001"), "should name the missing cert");
        }

        @Test
        @DisplayName("an expired certification does not count as held")
        void expiredDoesNotCount() {
            CapabilityProfile p = profile();
            p.getCertifications().add(Certification.builder()
                    .profile(p).code("ISO27001").name("expired")
                    .validUntil(LocalDate.now().minusDays(1)).build());

            assertEquals(RuleOutcome.Result.FAIL,
                    rule.evaluate(tender("ISO 27001 required."), p).result());
        }

        @Test
        @DisplayName("no certification demanded means the rule does not apply")
        void notApplicable() {
            assertEquals(RuleOutcome.Result.NOT_APPLICABLE,
                    rule.evaluate(tender("Bidder must have a valid trade licence."), profile())
                            .result());
        }
    }

    @Nested
    class Geography {
        private final GeographyRule rule = new GeographyRule();

        @Test
        @DisplayName("covered country passes")
        void covered() {
            assertEquals(RuleOutcome.Result.PASS,
                    rule.evaluate(tender("Open to all."), profile()).result());
        }

        @Test
        @DisplayName("uncovered country fails")
        void notCovered() {
            Tender t = tender("Open to all.");
            t.setCountry("Peru");
            CapabilityProfile p = profile();
            p.setGeographies(new java.util.ArrayList<>(List.of("Bangladesh")));
            assertEquals(RuleOutcome.Result.FAIL, rule.evaluate(t, p).result());
        }

        @Test
        @DisplayName("missing country is UNKNOWN, not a pass")
        void missingCountry() {
            Tender t = tender("Open to all.");
            t.setCountry(null);
            assertEquals(RuleOutcome.Result.UNKNOWN, rule.evaluate(t, profile()).result());
        }
    }

    @Nested
    class Money {
        @Test
        @DisplayName("South Asian and western units both normalise")
        void parsesUnits() {
            assertEquals(0, new BigDecimal("50000000")
                    .compareTo(MoneyTextParser.largest("BDT 5 crore")));
            assertEquals(0, new BigDecimal("5000000")
                    .compareTo(MoneyTextParser.largest("Tk. 50 lakh")));
            assertEquals(0, new BigDecimal("2000000")
                    .compareTo(MoneyTextParser.largest("USD 2 million")));
            assertEquals(0, new BigDecimal("5000000")
                    .compareTo(MoneyTextParser.largest("BDT 50,00,000")));
        }

        @Test
        @DisplayName("small bare numbers are not money")
        void ignoresSmallBareNumbers() {
            // Clause numbers, years and counts appear constantly in eligibility text.
            assertNull(MoneyTextParser.largest("Clause 5 of section 3, valid for 7 years"));
        }
    }
}
