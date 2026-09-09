package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.RuleOutcome;
import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.service.EligibilityRule;
import com.bracit.tendersense.util.MoneyTextParser;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimum annual turnover stated in the tender, against the profile's turnover. */
@Component
public class TurnoverRule implements EligibilityRule {

    public static final String CODE = "TURNOVER";

    /**
     * Only the sentence that actually mentions turnover is parsed. Eligibility text
     * is full of unrelated figures -- security deposits, document fees, contract
     * values -- and treating any of those as a turnover threshold produces confident
     * nonsense.
     */
    private static final Pattern TURNOVER_SENTENCE = Pattern.compile(
            "[^.;\\n]*\\b(?:annual\\s+turnover|average\\s+annual\\s+turnover|turnover)\\b[^.;\\n]*",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public RuleOutcome evaluate(Tender tender, CapabilityProfile profile) {
        String text = tender.getEligibilityText();
        if (text == null || text.isBlank()) {
            return RuleOutcome.unknown(CODE, "Tender states no eligibility text; verify manually.");
        }

        Matcher m = TURNOVER_SENTENCE.matcher(text);
        if (!m.find()) {
            return RuleOutcome.notApplicable(CODE);
        }

        BigDecimal required = MoneyTextParser.largest(m.group());
        if (required == null) {
            return RuleOutcome.unknown(CODE,
                    "Turnover is mentioned but no figure could be read; verify manually.");
        }

        BigDecimal ours = profile.getAnnualTurnoverBdt();
        if (ours == null) {
            return RuleOutcome.unknown(CODE,
                    "Capability profile has no annual turnover recorded.");
        }

        String requirement = "Minimum annual turnover " + money(required);
        String actual = "BracIT annual turnover " + money(ours);

        if (ours.compareTo(required) >= 0) {
            return RuleOutcome.pass(CODE, requirement, actual, "Turnover requirement met.");
        }
        BigDecimal shortfall = required.subtract(ours);
        return RuleOutcome.fail(CODE, requirement, actual,
                "Falls short of the turnover threshold by " + money(shortfall) + ".");
    }

    private static String money(BigDecimal v) {
        return "BDT " + NumberFormat.getIntegerInstance(Locale.ENGLISH).format(v);
    }
}
