package com.bracit.tendersense.controller;

import com.bracit.tendersense.entity.enums.Sector;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

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
                .map(s -> new SectorOption(s.name(), s.label()))
                .toList();
    }
}
