package com.bracit.tendersense.util;

import com.bracit.tendersense.entity.Tender;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the parser over the real Day-0 snapshot corpus rather than a single fixture.
 * A parser that works on one hand-picked page but breaks on RFQ variants is the
 * failure this guards against.
 *
 * <p>Skipped when the snapshot directory is absent, since it is gitignored.
 */
class EgpHtmlParserTest {

    private static final Path SNAPSHOT_DIR = Path.of("data/snapshots/egp/detail");
    private static final int SAMPLE_SIZE = 300;

    private final EgpHtmlParser parser = new EgpHtmlParser();

    private List<Path> sampleFiles() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(SNAPSHOT_DIR),
                "snapshot corpus not present - run the Day-0 snapshot first");
        try (Stream<Path> files = Files.list(SNAPSHOT_DIR)) {
            List<Path> sample = files.filter(p -> p.toString().endsWith(".html"))
                    .sorted()
                    .limit(SAMPLE_SIZE)
                    .toList();
            Assumptions.assumeFalse(sample.isEmpty(), "snapshot corpus is empty");
            return sample;
        }
    }

    @Test
    @DisplayName("parses the core fields across the whole snapshot sample")
    void parsesCorpus() throws IOException {
        List<Path> files = sampleFiles();

        int parsed = 0, withMinistry = 0, withNature = 0, withClosing = 0,
                withTitle = 0, withSubstantiveEligibility = 0;

        for (Path f : files) {
            String html = Files.readString(f, StandardCharsets.UTF_8);
            String id = f.getFileName().toString().replace(".html", "");

            Tender t = parser.parse(html, id, f.toString());
            parsed++;

            assertEquals(id, t.getExternalId(), "externalId must come from the filename/id");
            assertNotNull(t.getContentHash(), "content hash drives corrigendum detection");
            assertNotNull(t.getFirstSeenAt());

            if (notBlank(t.getMinistry())) withMinistry++;
            if (notBlank(t.getProcurementNature())) withNature++;
            if (t.getClosingAt() != null) withClosing++;
            if (notBlank(t.getTitle())) withTitle++;
            if (t.getEligibilityText() != null && t.getEligibilityText().length() > 40) {
                withSubstantiveEligibility++;
            }
        }

        System.out.printf("parsed=%d ministry=%d nature=%d closing=%d title=%d substantiveEligibility=%d%n",
                parsed, withMinistry, withNature, withClosing, withTitle, withSubstantiveEligibility);

        // These fields appear on every observed page shape, so near-total coverage
        // is the correct expectation; a regression here means the labels moved.
        assertTrue(withMinistry >= parsed * 0.95,
                "ministry parsed on only " + withMinistry + "/" + parsed);
        assertTrue(withNature >= parsed * 0.95,
                "procurementNature parsed on only " + withNature + "/" + parsed);
        assertTrue(withTitle >= parsed * 0.95,
                "title parsed on only " + withTitle + "/" + parsed);

        // Closing date is absent from a small minority of pages upstream (~5%),
        // so this threshold is deliberately lower than the others.
        assertTrue(withClosing >= parsed * 0.90,
                "closingAt parsed on only " + withClosing + "/" + parsed);

        // Roughly half of e-GP tenders give real eligibility text; the rest say
        // "As per TDS". That is why EligibilityStatus has a NEEDS_VERIFICATION state.
        assertTrue(withSubstantiveEligibility >= parsed * 0.25,
                "substantive eligibility text on only " + withSubstantiveEligibility + "/" + parsed);
    }

    @Test
    @DisplayName("label lookup ignores position and tolerates blank values")
    void labelValuePairing() {
        String html = """
                <table>
                  <tr><td>Ministry :</td><td>Ministry of Home Affairs</td>
                      <td>Division :</td><td></td></tr>
                  <tr><td>Procuring Entity Code :</td><td>Procuring Entity District :</td><td>Barishal</td></tr>
                </table>
                """;
        var f = parser.extractLabelValues(html);

        assertEquals("Ministry of Home Affairs", f.get("Ministry"));
        assertEquals("", f.get("Division"), "a blank cell must yield empty, not the next label");
        assertEquals("", f.get("Procuring Entity Code"),
                "a label followed directly by another label means the field was omitted");
        assertEquals("Barishal", f.get("Procuring Entity District"));
    }

    @Test
    @DisplayName("e-GP date and money formats")
    void parsesFormats() {
        assertEquals(2026, EgpHtmlParser.parseDateTime("21-Sep-2026 11:15").getYear());
        assertEquals(11, EgpHtmlParser.parseDateTime("21-Sep-2026 11:15").getHour());
        assertNull(EgpHtmlParser.parseDateTime("not a date"));
        assertNull(EgpHtmlParser.parseDateTime(""));

        assertEquals(0, new java.math.BigDecimal("2500").compareTo(EgpHtmlParser.parseMoney("2,500")));
        assertNull(EgpHtmlParser.parseMoney("N/A"));
    }

    @Test
    @DisplayName("no parsed field exceeds its database column length")
    void fieldsFitTheSchema() throws IOException {
        // A single overlong reference number failed a whole reconcile run before
        // this guard existed: the fallback for referenceNo is a description field.
        for (Path f : sampleFiles()) {
            String html = Files.readString(f, StandardCharsets.UTF_8);
            String id = f.getFileName().toString().replace(".html", "");
            Tender t = parser.parse(html, id, f.toString());

            assertMaxLength(id, "referenceNo", t.getReferenceNo(), 512);
            assertMaxLength(id, "title", t.getTitle(), 2000);
            assertMaxLength(id, "procurementNature", t.getProcurementNature(), 128);
            assertMaxLength(id, "procurementType", t.getProcurementType(), 64);
            assertMaxLength(id, "procurementMethod", t.getProcurementMethod(), 256);
            assertMaxLength(id, "ministry", t.getMinistry(), 512);
            assertMaxLength(id, "division", t.getDivision(), 512);
            assertMaxLength(id, "organization", t.getOrganization(), 512);
            assertMaxLength(id, "procuringEntity", t.getProcuringEntity(), 512);
            assertMaxLength(id, "peCode", t.getPeCode(), 64);
            assertMaxLength(id, "district", t.getDistrict(), 128);
            assertMaxLength(id, "country", t.getCountry(), 128);
            assertMaxLength(id, "budgetType", t.getBudgetType(), 128);
            assertMaxLength(id, "sourceOfFunds", t.getSourceOfFunds(), 256);
            assertMaxLength(id, "status", t.getStatus(), 64);
        }
    }

    @Test
    @DisplayName("consultancy notices use Consultant labels and still parse")
    void parsesConsultancyVariant() {
        String html = """
                <table>
                  <tr><td>Closing Date and Time :</td><td>21-Sep-2026 11:15</td></tr>
                  <tr><td>Eligibility of Consultant :</td><td>Firm must hold ISO 27001.</td></tr>
                  <tr><td>Experience, Resources and delivery capacity required :</td>
                      <td>Five similar MIS assignments in the last seven years.</td></tr>
                  <tr><td>Brief Description of assignment :</td><td>MIS development for a programme.</td></tr>
                </table>
                """;
        Tender t = parser.parse(html, "999", null);

        assertNotNull(t.getClosingAt(), "consultancy pages use 'Closing Date and Time'");
        assertNotNull(t.getEligibilityText());
        assertTrue(t.getEligibilityText().contains("ISO 27001"));
        assertTrue(t.getEligibilityText().contains("similar MIS assignments"),
                "the experience block states hard requirements and must reach the rules engine");
        assertTrue(t.getDescription().contains("MIS development"));
    }

    @Test
    @DisplayName("identical eligibility and experience text is not duplicated")
    void doesNotDuplicateBoilerplate() {
        String html = """
                <table>
                  <tr><td>Eligibility of Consultant :</td><td>As mentioned in the Tender Document</td></tr>
                  <tr><td>Experience, Resources and delivery capacity required :</td>
                      <td>As mentioned in the Tender Document</td></tr>
                </table>
                """;
        Tender t = parser.parse(html, "998", null);
        assertEquals("As mentioned in the Tender Document", t.getEligibilityText());
    }

    private static void assertMaxLength(String id, String field, String value, int max) {
        if (value != null && value.length() > max) {
            fail("tender %s: %s is %d chars, column allows %d"
                    .formatted(id, field, value.length(), max));
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
