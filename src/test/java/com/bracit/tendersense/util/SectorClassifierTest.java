package com.bracit.tendersense.util;

import com.bracit.tendersense.entity.enums.Sector;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the classifier over the real snapshot corpus. A rule set that works on the twelve
 * examples it was written from and collapses on the long tail is the failure this guards
 * against — 277 distinct CPV top-level values were observed, and most rules were written
 * from the top 40.
 */
class SectorClassifierTest {

    private static final Path SNAPSHOT_DIR = Path.of("data/snapshots/egp/detail");
    private static final int SAMPLE_SIZE = 800;

    private final SectorClassifier classifier = new SectorClassifier();
    private final EgpHtmlParser parser = new EgpHtmlParser();

    @BeforeEach
    void setUp() {
        classifier.load();
    }

    @Test
    @DisplayName("ordering rules that are easy to break")
    void orderingIsLoadBearing() {
        // Contains the word "construction" but is CPV 71 professional services.
        assertEquals(Sector.CONSULTANCY, classifier.classify(
                "Architectural, engineering, construction, legal, accounting and other professional services",
                null, null),
                "the consultancy rule must be checked before the construction rule");

        // Contains "computer" but is a services division.
        assertEquals(Sector.IT_SERVICES,
                classifier.classify("Computer and related services", null, null));

        // Contains "computing" and is hardware.
        assertEquals(Sector.IT_HARDWARE,
                classifier.classify("Office and computing machinery, equipment and supplies", null, null));

        // Contains "roads" but is maintenance, not construction.
        assertEquals(Sector.MAINTENANCE, classifier.classify(
                "Repair, maintenance and associated services related to roads and other equipment",
                null, null));
    }

    @Test
    @DisplayName("the headline CPV divisions land where a bidder would expect")
    void knownDivisions() {
        Map<String, Sector> expected = Map.of(
                "Construction work", Sector.CONSTRUCTION,
                "Medical and laboratory devices, optical and precision devices", Sector.MEDICAL,
                "Food products and beverages", Sector.FOOD,
                "Electrical machinery, apparatus, equipment and consumables", Sector.ELECTRICAL,
                "Various types of printed matter and articles for printing", Sector.PRINTING,
                "Motor vehicles, trailers and vehicle parts", Sector.VEHICLES,
                "Textiles and textile articles", Sector.TEXTILES,
                "Collected and purified water", Sector.UTILITIES,
                "Agricultural, horticultural, hunting and related products", Sector.AGRICULTURE);

        expected.forEach((cpv, sector) ->
                assertEquals(sector, classifier.classify(cpv, null, null), "misclassified: " + cpv));
    }

    @Test
    @DisplayName("the CPV division decides, not a deep sub-category")
    void divisionBeatsSubcategory() {
        // A real corpus shape: the division is machinery, but the path mentions telephones.
        assertEquals(Sector.MACHINERY, classifier.classify(
                "Machinery for the production and use of mechanical power;Parts;Telephone line equipment",
                null, null),
                "a deep sub-category must not override the division");

        assertEquals(Sector.CONSTRUCTION, classifier.classify(
                "Construction work;Building installation work;Telephone and network cabling",
                null, null));

        // But when the division itself is telecoms, TELECOM is right.
        assertEquals(Sector.TELECOM, classifier.classify(
                "Radio, television, communication, telecommunication and related equipment;Transmitters",
                null, null));
    }

    @Test
    @DisplayName("falls back to procurement method, then title, then OTHER")
    void fallbackChain() {
        assertEquals(Sector.CONSULTANCY, classifier.classify(null, "QCBS", null));
        assertEquals(Sector.CONSULTANCY,
                classifier.classify(null, "Quality and Cost Based Selection (QCBS)", null));

        // No CPV, a goods method, but the title is unambiguous.
        assertEquals(Sector.IT_HARDWARE,
                classifier.classify(null, "OTM", "Procurement of computer equipment for the division"));

        assertEquals(Sector.OTHER, classifier.classify(null, null, null));
        assertEquals(Sector.OTHER, classifier.classify("", "", ""));
    }

    @Test
    @DisplayName("World Bank groups map, but a clear title wins over the coarse group")
    void worldBank() {
        assertEquals(Sector.CONSTRUCTION, classifier.classifyWorldBank("CW", null, "Bridge rehabilitation"));
        assertEquals(Sector.CONSULTANCY,
                classifier.classifyWorldBank("CS", null, "Financial management adviser"));
        // "CS" is every kind of consultancy; the title says which kind.
        assertEquals(Sector.IT_SERVICES, classifier.classifyWorldBank(
                "CS", null, "Consulting firm for development of an information technology platform"));
    }

    @Test
    @DisplayName("classifies the real corpus, with OTHER staying a small minority")
    void classifiesCorpus() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(SNAPSHOT_DIR), "snapshot corpus required");
        List<Path> files;
        try (Stream<Path> s = Files.list(SNAPSHOT_DIR)) {
            files = s.filter(p -> p.toString().endsWith(".html")).sorted().limit(SAMPLE_SIZE).toList();
        }
        Assumptions.assumeFalse(files.isEmpty(), "snapshot corpus is empty");

        EnumMap<Sector, Integer> counts = new EnumMap<>(Sector.class);
        int total = 0;

        for (Path f : files) {
            Map<String, String> fields =
                    parser.extractLabelValues(Files.readString(f, StandardCharsets.UTF_8));
            Sector s = classifier.classify(
                    fields.get("Category"),
                    fields.get("Procurement Method"),
                    fields.get("Tender/Proposal Package No. and Description"));
            counts.merge(s, 1, Integer::sum);
            total++;
        }

        int other = counts.getOrDefault(Sector.OTHER, 0);
        System.out.printf("classified %d tenders, OTHER=%d (%.1f%%)%n",
                total, other, 100.0 * other / total);
        counts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> System.out.printf("   %-16s %d%n", e.getKey(), e.getValue()));

        assertTrue(other <= total * 0.15,
                "OTHER is %d/%d — the rule set is not covering the corpus".formatted(other, total));
        assertTrue(counts.size() >= 8,
                "only %d distinct sectors found; the rules are collapsing everything".formatted(counts.size()));
    }
}
