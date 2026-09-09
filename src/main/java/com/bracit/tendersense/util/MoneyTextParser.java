package com.bracit.tendersense.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts monetary amounts from free-text tender requirements.
 *
 * <p>Bangladeshi procurement text mixes plain figures with South Asian units, so
 * "BDT 5 crore", "Tk. 50,00,000" and "USD 2 million" all have to resolve to a
 * comparable number before a turnover threshold can be checked.
 */
public final class MoneyTextParser {

    private static final Pattern AMOUNT = Pattern.compile(
            "(?:bdt|tk\\.?|taka|usd|\\$)?\\s*([0-9][0-9,.]*)\\s*"
                    + "(crore|koti|lakh|lac|million|billion|mn|bn)?",
            Pattern.CASE_INSENSITIVE);

    private MoneyTextParser() {
    }

    /** All amounts found, normalised to base units, largest first. */
    public static List<BigDecimal> amounts(String text) {
        List<BigDecimal> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        Matcher m = AMOUNT.matcher(text);
        while (m.find()) {
            String digits = m.group(1).replace(",", "");
            if (digits.isEmpty() || digits.equals(".")) {
                continue;
            }
            BigDecimal value;
            try {
                value = new BigDecimal(digits);
            } catch (NumberFormatException e) {
                continue;
            }
            BigDecimal scaled = value.multiply(multiplier(m.group(2)));

            // Bare small numbers are years, counts or clause numbers, not money.
            if (m.group(2) == null && scaled.compareTo(BigDecimal.valueOf(100_000)) < 0) {
                continue;
            }
            out.add(scaled);
        }
        out.sort((a, b) -> b.compareTo(a));
        return out;
    }

    public static BigDecimal largest(String text) {
        List<BigDecimal> all = amounts(text);
        return all.isEmpty() ? null : all.get(0);
    }

    private static BigDecimal multiplier(String unit) {
        if (unit == null) {
            return BigDecimal.ONE;
        }
        return switch (unit.toLowerCase(Locale.ENGLISH)) {
            case "crore", "koti" -> BigDecimal.valueOf(10_000_000L);
            case "lakh", "lac" -> BigDecimal.valueOf(100_000L);
            case "million", "mn" -> BigDecimal.valueOf(1_000_000L);
            case "billion", "bn" -> BigDecimal.valueOf(1_000_000_000L);
            default -> BigDecimal.ONE;
        };
    }
}
