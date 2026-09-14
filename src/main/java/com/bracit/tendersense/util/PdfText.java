package com.bracit.tendersense.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;

/** The text of a PDF, via Apache PDFBox. Scanned pages have none and come back empty. */
public final class PdfText {

    private PdfText() {
    }

    /** True when the bytes start like a PDF -- a portal's error page often arrives with a 200. */
    public static boolean looksLikePdf(byte[] bytes) {
        return bytes != null && bytes.length > 4
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    /**
     * @param maxPages read at most this many pages from the start
     * @throws IOException for a damaged or password-protected file
     */
    public static String extract(byte[] pdf, int maxPages) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // Reading order follows the page, not the order the text was drawn in:
            // table cells otherwise come out scrambled.
            stripper.setSortByPosition(true);
            stripper.setEndPage(Math.min(doc.getNumberOfPages(), Math.max(1, maxPages)));
            return stripper.getText(doc);
        }
    }
}
