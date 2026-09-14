package com.bracit.tendersense.util;

import com.bracit.tendersense.entity.enums.Sector;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tags a tender with a {@link Sector}, using signals the portal already supplies.
 *
 * <p>No model is involved. e-GP carries a CPV category on every tender observed, and CPV
 * is a published international procurement vocabulary — classifying against it is a
 * lookup, and the result is auditable in a way an embedding's argmax is not.
 *
 * <p>Rules are <b>ordered and first-match-wins</b>, which is load-bearing: the CPV label
 * "Architectural, engineering, construction, legal, accounting..." is professional
 * services, not construction, and only survives because the consultancy rule is checked
 * first. Keep {@code cpv-sector-map.json} in its authored order.
 *
 * <p>Fallback chain: CPV → procurement method → title → {@link Sector#OTHER}.
 */
@Component
@Slf4j
public class SectorClassifier {

    private static final String MAP_RESOURCE = "data/cpv-sector-map.json";

    private record Rule(Sector sector, List<String> patterns) {
    }

    private List<Rule> rules = List.of();
    private List<String> consultancyMethods = List.of();
    private Map<String, Sector> worldBankGroups = Map.of();

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource(MAP_RESOURCE).getInputStream()) {
            JsonNode root = new ObjectMapper().readTree(in);

            List<Rule> loaded = new ArrayList<>();
            for (JsonNode r : root.path("rules")) {
                List<String> patterns = new ArrayList<>();
                r.path("patterns").forEach(p -> patterns.add(p.asString().toLowerCase(Locale.ENGLISH)));
                loaded.add(new Rule(Sector.valueOf(r.path("sector").asString()), List.copyOf(patterns)));
            }
            rules = List.copyOf(loaded);

            List<String> methods = new ArrayList<>();
            root.path("methodConsultancy").forEach(m -> methods.add(m.asString().toLowerCase(Locale.ENGLISH)));
            consultancyMethods = List.copyOf(methods);

            Map<String, Sector> groups = new LinkedHashMap<>();
            root.path("worldBankGroup").properties()
                    .forEach(e -> groups.put(e.getKey(), Sector.valueOf(e.getValue().asString())));
            worldBankGroups = Map.copyOf(groups);

            log.info("sector classifier loaded: {} rules, {} consultancy methods, {} WB groups",
                    rules.size(), consultancyMethods.size(), worldBankGroups.size());
        } catch (Exception e) {
            log.error("could not load {} — every tender will classify as OTHER: {}",
                    MAP_RESOURCE, e.getMessage());
        }
    }

    /**
     * @param cpv    the portal's category string, semicolon-delimited broad-to-narrow
     * @param method procurement method, used when CPV is silent
     * @param title  last resort before OTHER
     */
    public Sector classify(String cpv, String method, String title) {
        // CPV reads broad-to-narrow, so the FIRST segment is the division that decides
        // the sector. Matching the whole hierarchy string tagged construction tenders as
        // TELECOM because a deep sub-category mentioned "telephone" — check the division
        // first, and only widen to the full path when it yields nothing.
        Sector fromDivision = matchRules(topLevel(cpv));
        if (fromDivision != null) {
            return fromDivision;
        }
        Sector fromCpv = matchRules(cpv);
        if (fromCpv != null) {
            return fromCpv;
        }
        if (isConsultancyMethod(method)) {
            return Sector.CONSULTANCY;
        }
        Sector fromTitle = matchRules(title);
        if (fromTitle != null) {
            return fromTitle;
        }
        return Sector.OTHER;
    }

    /** World Bank notices carry no CPV; {@code procurement_group} does the same job. */
    public Sector classifyWorldBank(String procurementGroup, String noticeType, String title) {
        if (procurementGroup != null) {
            Sector mapped = worldBankGroups.get(procurementGroup.trim().toUpperCase(Locale.ENGLISH));
            // A title match beats the coarse group: "CS" covers every kind of consultancy,
            // and an IT systems assignment should tag as IT_SERVICES rather than CONSULTANCY.
            if (mapped != null) {
                Sector fromTitle = matchRules(title);
                return fromTitle != null ? fromTitle : mapped;
            }
        }
        return classify(null, noticeType, title);
    }

    /** The CPV division a tender sits in, for display and drill-down. */
    public String topLevel(String cpv) {
        if (cpv == null || cpv.isBlank()) {
            return null;
        }
        String first = cpv.split(";")[0].trim();
        return first.isEmpty() ? null : first;
    }

    private Sector matchRules(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String haystack = text.toLowerCase(Locale.ENGLISH);
        for (Rule rule : rules) {
            for (String pattern : rule.patterns()) {
                if (haystack.contains(pattern)) {
                    return rule.sector();
                }
            }
        }
        return null;
    }

    private boolean isConsultancyMethod(String method) {
        if (method == null || method.isBlank()) {
            return false;
        }
        String m = method.toLowerCase(Locale.ENGLISH);
        return consultancyMethods.stream().anyMatch(m::contains);
    }
}
