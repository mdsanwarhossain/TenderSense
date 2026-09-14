package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.BracProperties;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.NoticeType;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.entity.enums.TenderCategory;
import com.bracit.tendersense.util.PdfText;
import com.bracit.tendersense.util.TenderStandardiser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BracTenderFetchServiceImplTest {

    /** Two rows exactly as the live list returned them on 12 Sep 2026. */
    private static final List<String> RFQ_ROW = List.of("14998", "BPD/2026/RFQ-2408",
            "RFQ for the Laptop [BPD/2026/RFQ-2408]", "QM", "Single Envelop", "11/09/2026 03:03 PM",
            "14/09/2026 02:30 PM", "14/09/2026 03:00 PM", "", "[00257966]-MD SHAHIDUL HASAN", "Published", "00257966");
    private static final List<String> RFP_ROW = List.of("14901", "BPD/2026/RFP-2344",
            "Letter of Invitation for hiring Partnerships Consultant for BRAC [BPD/2026/RFP-2344]", "PM",
            "Single Envelop", "01/09/2026 10:00 AM", "21/09/2026 02:30 PM", "21/09/2026 03:00 PM", "",
            "[00123456]-SOMEONE", "Published", "00123456");

    @Test
    @DisplayName("a list row becomes a tender: id, number, clean title, dates in Dhaka time, method")
    void mapsAListRow() {
        Tender t = BracTenderFetchServiceImpl.toTender(RFQ_ROW);

        assertEquals(SourcePortal.BRAC, t.getSourcePortal());
        assertEquals("14998", t.getExternalId());
        assertEquals("BPD/2026/RFQ-2408", t.getReferenceNo());
        assertEquals("RFQ for the Laptop", t.getTitle(), "the tender number is not repeated in the title");
        assertEquals("RFQ", t.getProcurementType());
        assertEquals("Goods", t.getProcurementNature());
        assertEquals(LocalDateTime.of(2026, 9, 11, 15, 3), t.getPublishedAt());
        assertEquals(LocalDateTime.of(2026, 9, 14, 14, 30), t.getClosingAt(), "closing = last submission");
        assertEquals("Published", t.getStatus());
        assertNotNull(t.getContentHash());
        assertEquals(BracTenderFetchServiceImpl.PARSER_VERSION, t.getParserVersion());
    }

    @Test
    @DisplayName("in the standard form it reads as BRAC's own quotation request for goods")
    void standardForm() {
        Tender t = BracTenderFetchServiceImpl.toTender(RFQ_ROW);
        TenderStandardiser.apply(t);
        assertEquals("BRAC", t.getBuyer());
        assertEquals("Request for quotations", t.getMethodLabel());
        assertEquals(TenderCategory.GOODS, t.getCategory());
        assertEquals(NoticeType.TENDER, t.getNoticeType());
        assertEquals("Bangladesh", t.getLocation());

        Tender rfp = BracTenderFetchServiceImpl.toTender(RFP_ROW);
        TenderStandardiser.apply(rfp);
        assertEquals("Request for proposals", rfp.getMethodLabel());
        assertEquals(TenderCategory.CONSULTING, rfp.getCategory(), "a request for proposals hires a consultant");
    }

    @Test
    @DisplayName("building work reads as works, and an unknown method code is kept as it came")
    void natureAndMethod() {
        assertEquals("Works", BracTenderFetchServiceImpl.nature("IFT", "IFT for Construction of a two-storied school building"));
        assertEquals("Goods", BracTenderFetchServiceImpl.nature("IFT", "IFT for the wide mouth semen container"));
        assertEquals("IFT", BracTenderFetchServiceImpl.kind("Re-BPD/2026/IFT-2279/v1"));
        Tender t = BracTenderFetchServiceImpl.toTender(List.of("1", "X/1", "Title", "ZZ", "", "", "", "", "", "", "", ""));
        assertEquals("ZZ", t.getProcurementMethod());
        assertNull(t.getClosingAt(), "no date is no date, not a guess");
    }

    @Test
    @DisplayName("the staff member who raised a tender is never stored")
    void noStaffDetailsInThePayload() {
        String payload = BracTenderFetchServiceImpl.sanitisedPayload(RFQ_ROW);
        assertTrue(payload.contains("BPD/2026/RFQ-2408"));
        assertFalse(payload.contains("SHAHIDUL"), payload);
        assertFalse(payload.contains("00257966"), payload);
        assertEquals(payload, BracTenderFetchServiceImpl.toTender(RFQ_ROW).getRawPayload());
    }

    @Test
    @DisplayName("document text keeps the items and terms, drops the author's contact line and print stamps")
    void cleansDocumentText() {
        String raw = """
                Date: 10-09-2026   Req. No.:  REQ20260018038
                ........................................
                ........................................  Date:  10-09-2026
                Subject: RFQ for the Laptop

                Lot-1   Laptop   Intel Core Ultra 5, 16 GB DDR5      Pcs   11


                BRAC  T: 880-2-9881265  Registered in
                Name: MD SHAHIDUL HASAN, Designation: Manager, Procurement, Email: shahidul.hasan@brac.net, Phone: 01730351408
                https://erp.brac.net  Printing Date & Time: 12-09-26 02:33 AM  Page 1 of 4
                Terms & Conditions:
                """;
        String clean = BracTenderFetchServiceImpl.cleanDocumentText(raw, 10_000);

        assertTrue(clean.contains("Subject: RFQ for the Laptop"));
        assertTrue(clean.contains("Lot-1 Laptop Intel Core Ultra 5, 16 GB DDR5 Pcs 11"), clean);
        assertTrue(clean.contains("Terms & Conditions:"));
        assertTrue(clean.contains("\nDate: 10-09-2026\n"), "text after a form blank is kept, the dots are not: " + clean);
        for (String gone : List.of("SHAHIDUL", "01730351408", "shahidul.hasan", "Printing Date", "....", "880-2-9881265")) {
            assertFalse(clean.contains(gone), "should be gone: " + gone + "\n" + clean);
        }
        assertFalse(clean.contains("\n\n\n"), "runs of blank lines collapse to one");
        assertEquals(12, BracTenderFetchServiceImpl.cleanDocumentText(raw, 12).length(), "capped to the limit");
    }

    @Test
    @DisplayName("PDFBox reads the text back out of a real PDF")
    void readsPdfText() throws Exception {
        byte[] pdf;
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream text = new PDPageContentStream(doc, page)) {
                text.beginText();
                text.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                text.newLineAtOffset(72, 700);
                text.showText("Lot-1 Laptop Intel Core Ultra 5 Pcs 11");
                text.endText();
            }
            doc.save(out);
            pdf = out.toByteArray();
        }

        assertTrue(PdfText.looksLikePdf(pdf));
        assertFalse(PdfText.looksLikePdf("<html>Not found</html>".getBytes()), "an error page is not a PDF");
        assertTrue(PdfText.extract(pdf, 20).contains("Lot-1 Laptop Intel Core Ultra 5 Pcs 11"));
    }

    @Test
    @DisplayName("live: the list endpoint still answers in the shape this reads (skipped offline)")
    void liveListShape() {
        BracTenderFetchServiceImpl service =
                new BracTenderFetchServiceImpl(RestClient.builder().build(), new BracProperties());
        BracTenderFetchServiceImpl.ListingPage page;
        try {
            page = service.listing(1);
        } catch (Exception e) {
            Assumptions.abort("BRAC e-Tender unreachable: " + e.getMessage());
            return;
        }
        assertTrue(page.records() >= 0);
        if (!page.rows().isEmpty()) {
            List<String> cell = page.rows().get(0).cell();
            assertEquals(12, cell.size(), "column order changed on the site: " + cell);
            assertNotNull(BracTenderFetchServiceImpl.toTender(cell).getClosingAt(),
                    "last submission date no longer parses: " + cell.get(BracTenderFetchServiceImpl.LAST_SUBMISSION));
        }
    }
}
