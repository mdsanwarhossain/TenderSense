package com.bracit.tendersense.controller;

import com.bracit.tendersense.entity.enums.Sector;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The sector vocabulary, for the sign-up form and the profile editor.
 *
 * <p>Unauthenticated by design -- sign-up needs it before an account exists, and a list
 * of procurement categories is not information worth protecting. Served from the enum so
 * the two ends cannot drift: adding a {@link Sector} makes it selectable with no
 * frontend change.
 */
@RestController
@RequestMapping("/api/sectors")
public class SectorController {

    public record SectorOption(String value, String label) {}

    @GetMapping
    public List<SectorOption> list() {
        return Arrays.stream(Sector.values())
                .filter(s -> s != Sector.OTHER)   // a bucket for classification misses,
                                                  // never something a company subscribes to
                .map(s -> new SectorOption(s.name(), label(s)))
                .toList();
    }

    /** {@code IT_SERVICES} reads as noise in a chip; "IT services" does not. */
    private static String label(Sector sector) {
        String[] words = sector.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (i == 0) {
                out.append(w.equals("it") ? "IT" : Character.toUpperCase(w.charAt(0)) + w.substring(1));
            } else {
                out.append(' ').append(w);
            }
        }
        return out.toString();
    }
}
