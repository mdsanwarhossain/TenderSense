package com.bracit.tendersense.util;

import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.NoticeType;
import com.bracit.tendersense.entity.enums.OpenTo;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.entity.enums.TenderCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Every value below was read out of the database -- these are the portals' real words. */
class TenderStandardiserTest {

    @Test
    @DisplayName("both portals' category words land on one vocabulary")
    void categories() {
        assertEquals(TenderCategory.GOODS, TenderStandardiser.category(SourcePortal.EGP_BANGLADESH, "Goods"));
        assertEquals(TenderCategory.GOODS, TenderStandardiser.category(SourcePortal.EGP_BANGLADESH, "Goods (Framework Agreement)"));
        assertEquals(TenderCategory.WORKS, TenderStandardiser.category(SourcePortal.EGP_BANGLADESH, "Works"));
        assertEquals(TenderCategory.OTHER_SERVICES, TenderStandardiser.category(SourcePortal.EGP_BANGLADESH, "Physical Services"));
        assertEquals(TenderCategory.CONSULTING, TenderStandardiser.category(SourcePortal.EGP_BANGLADESH, "Services"));
        assertEquals(TenderCategory.GOODS, TenderStandardiser.category(SourcePortal.WORLD_BANK, "GO"));
        assertEquals(TenderCategory.WORKS, TenderStandardiser.category(SourcePortal.WORLD_BANK, "CW"));
        assertEquals(TenderCategory.CONSULTING, TenderStandardiser.category(SourcePortal.WORLD_BANK, "CS"));
        assertEquals(TenderCategory.OTHER_SERVICES, TenderStandardiser.category(SourcePortal.WORLD_BANK, "NC"));
    }

    @Test
    @DisplayName("World Bank contract awards are recognised as not biddable")
    void noticeTypes() {
        assertEquals(NoticeType.CONTRACT_AWARD, TenderStandardiser.noticeType(SourcePortal.WORLD_BANK, "Contract Award"));
        assertFalse(NoticeType.CONTRACT_AWARD.isOpportunity());
        assertEquals(NoticeType.TENDER, TenderStandardiser.noticeType(SourcePortal.WORLD_BANK, "Invitation for Bids"));
        assertEquals(NoticeType.EXPRESSION_OF_INTEREST,
                TenderStandardiser.noticeType(SourcePortal.WORLD_BANK, "Request for Expression of Interest"));
        assertEquals(NoticeType.GENERAL_NOTICE, TenderStandardiser.noticeType(SourcePortal.WORLD_BANK, "General Procurement Notice"));
        assertEquals(NoticeType.PREQUALIFICATION, TenderStandardiser.noticeType(SourcePortal.WORLD_BANK, "Invitation for Prequalification"));
        assertEquals(NoticeType.EXPRESSION_OF_INTEREST, TenderStandardiser.noticeType(SourcePortal.EGP_BANGLADESH, "REOI"));
        assertEquals(NoticeType.TENDER, TenderStandardiser.noticeType(SourcePortal.EGP_BANGLADESH, null));
    }

    @Test
    @DisplayName("the same method spelled two ways reads the same")
    void methods() {
        assertEquals("Quality and cost based selection", TenderStandardiser.method("Quality Cost Based Selection (QCBS)"));
        assertEquals("Quality and cost based selection", TenderStandardiser.method("Quality And Cost-Based Selection"));
        assertEquals("Open tender", TenderStandardiser.method("Open Tendering Method (OTM)"));
        assertEquals("Individual consultant", TenderStandardiser.method("Individual Consultant Selection"));
        assertEquals("Consultant qualification selection", TenderStandardiser.method("Consultant Qualification  Selection"));
        assertEquals("Some new method", TenderStandardiser.method("Some New Method (SNM)"));
    }

    @Test
    @DisplayName("amendment counts and national / international come out of e-GP's fields")
    void statusAndOpenTo() {
        assertEquals(3, TenderStandardiser.amendments("Amendment/Corrigendum issued : 3"));
        assertEquals(0, TenderStandardiser.amendments("Live"));
        assertEquals(OpenTo.NATIONAL, TenderStandardiser.openTo("NCT"));
        assertEquals(OpenTo.INTERNATIONAL, TenderStandardiser.openTo("ICT"));
    }

    @Test
    @DisplayName("a World Bank notice gets its district and buyer from the e-GP text inside it")
    void worldBankLabelsFromNoticeText() {
        Tender t = Tender.builder().sourcePortal(SourcePortal.WORLD_BANK)
                .organization("Strengthening Revenue Administration Project")
                .country("Bangladesh")
                .procurementNature("GO").procurementType("Invitation for Bids")
                .description("Ministry : Ministry of Finance Organization : National Board of Revenue "
                        + "Procuring Entity Name : Office of the Project Director (SRAP), National Board of Revenue "
                        + "Procuring Entity Code : Procuring Entity District : Dhaka Procurement Nature : Goods "
                        + "Procurement Type : NCT Event Type : TENDER")
                .build();
        TenderStandardiser.apply(t);
        assertEquals("Dhaka", t.getLocation());
        assertEquals("Office of the Project Director (SRAP), National Board of Revenue", t.getBuyer());
        assertEquals("Strengthening Revenue Administration Project", t.getPartOf());
        assertEquals("World Bank", t.getFundedBy());
        assertEquals(TenderCategory.GOODS, t.getCategory());
    }

    @Test
    @DisplayName("an empty field in the notice text is left empty, not filled with the next label")
    void emptyLabelledField() {
        String text = "Procuring Entity Name : Procuring Entity Code : Procuring Entity District : "
                + "Procurement Nature : Goods Procurement Type : NCT";
        assertNull(TenderStandardiser.labelled(text, "Procuring Entity Name"));
        assertNull(TenderStandardiser.labelled(text, "Procuring Entity District"));
        assertEquals("Goods", TenderStandardiser.labelled(text, "Procurement Nature"));
    }

    @Test
    @DisplayName("an e-GP tender fills every generic field from its own labels")
    void egpTender() {
        Tender t = Tender.builder().sourcePortal(SourcePortal.EGP_BANGLADESH)
                .ministry("Ministry of Planning").division("Statistics and Informatics Division")
                .organization("Bangladesh Bureau of Statistics (BBS)")
                .procuringEntity("Strengthening GIS and Geocoding System of BBS Project")
                .district("Dhaka").country("Bangladesh").sourceOfFunds("Government")
                .procurementNature("Services").procurementType("NCT").noticeTypeRaw("REOI")
                .procurementMethod("Individual Consultant (IC)").status("Live")
                .build();
        TenderStandardiser.apply(t);
        assertEquals("Strengthening GIS and Geocoding System of BBS Project, Bangladesh Bureau of Statistics (BBS)", t.getBuyer());
        assertEquals("Ministry of Planning › Statistics and Informatics Division", t.getPartOf());
        assertEquals("Dhaka", t.getLocation());
        assertEquals(TenderCategory.CONSULTING, t.getCategory());
        assertEquals(NoticeType.EXPRESSION_OF_INTEREST, t.getNoticeType());
        assertEquals(OpenTo.NATIONAL, t.getOpenTo());
        assertEquals("Individual consultant", t.getMethodLabel());
        assertEquals("Government", t.getFundedBy());
        assertEquals(0, t.getAmendments());
    }
}
