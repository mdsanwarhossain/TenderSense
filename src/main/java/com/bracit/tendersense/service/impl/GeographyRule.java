package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.RuleOutcome;
import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.service.EligibilityRule;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Whether BracIT may bid in the tender's jurisdiction.
 *
 * <p>Also catches the national-only restriction e-GP encodes as procurement type
 * NCT (National Competitive Tendering) -- which BracIT satisfies as a Bangladeshi
 * firm, but which would exclude a foreign bidder, so it is checked rather than assumed.
 */
@Component
public class GeographyRule implements EligibilityRule {

    public static final String CODE = "GEOGRAPHY";

    private static final Pattern LOCAL_REGISTRATION = Pattern.compile(
            "locally\\s+registered|registered\\s+in\\s+bangladesh|national\\s+firms?\\s+only",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public RuleOutcome evaluate(Tender tender, CapabilityProfile profile) {
        String country = tender.getCountry();
        if (country == null || country.isBlank()) {
            return RuleOutcome.unknown(CODE, "Tender does not state a country; verify manually.");
        }

        boolean covered = profile.getGeographies().stream()
                .anyMatch(g -> matches(g, country, tender));

        String requirement = "Operates in " + country;
        String actual = "BracIT covers " + String.join(", ", profile.getGeographies());

        if (covered) {
            return RuleOutcome.pass(CODE, requirement, actual, "Geography is covered.");
        }

        String text = tender.getEligibilityText();
        if (text != null && LOCAL_REGISTRATION.matcher(text).find()) {
            return RuleOutcome.fail(CODE, requirement + " with local registration", actual,
                    "Tender is restricted to locally registered firms in " + country + ".");
        }
        return RuleOutcome.fail(CODE, requirement, actual,
                "BracIT's declared geographies do not cover " + country + ".");
    }

    private static boolean matches(String geography, String country, Tender tender) {
        String g = geography.toLowerCase(Locale.ENGLISH);
        String c = country.toLowerCase(Locale.ENGLISH);
        if (g.equals(c) || c.contains(g) || g.contains(c)) {
            return true;
        }
        // "International" covers anything the multilateral portals publish.
        if (g.equals("international") && tender.getSourcePortal() == SourcePortal.WORLD_BANK) {
            return true;
        }
        return false;
    }
}
