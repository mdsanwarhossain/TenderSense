package com.bracit.tendersense.dto;

import com.bracit.tendersense.entity.enums.AiStatus;
import com.bracit.tendersense.entity.enums.NoticeType;
import com.bracit.tendersense.entity.enums.OpenTo;
import com.bracit.tendersense.entity.enums.Sector;
import com.bracit.tendersense.entity.enums.TenderCategory;
import com.bracit.tendersense.entity.enums.SourcePortal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record TenderDetailResponse(
        Long id,
        String externalId,
        SourcePortal source,
        String referenceNo,
        String title,
        String description,
        String ministry,
        String division,
        String organization,
        String procuringEntity,
        String district,
        String country,
        String procurementNature,
        String procurementType,
        String procurementMethod,
        String budgetType,
        String sourceOfFunds,
        BigDecimal documentPriceBdt,
        LocalDateTime publishedAt,
        LocalDateTime closingAt,
        Integer daysToDeadline,
        String status,
        String eligibilityText,
        /** Provenance: the raw snapshot this record was parsed from. */
        String rawSnapshotPath,
        String contentHash,
        int revisionCount,
        Sector sector,
        /** The sector as the screen shows it, e.g. "IT services". */
        String sectorLabel,
        /** The acting company's tracking of this tender -- same meaning as on the list. */
        boolean wishlisted,
        boolean submitted,
        Instant submittedAt,
        /** The tender's own page on its portal; null when no verified link exists. */
        String sourceUrl,
        // ---- Standard form: the same labels for every portal (TenderStandardiser) ----
        String buyer,
        String partOf,
        String location,
        TenderCategory category,
        NoticeType noticeType,
        OpenTo openTo,
        String methodLabel,
        String fundedBy,
        Integer amendments,
        // ---- Read by the local model; null until read, or where a field failed its check ----
        String aiShortTitle,
        String aiSummary,
        List<String> aiDeliverables,
        String aiLocation,
        BigDecimal aiMinTurnoverBdt,
        Integer aiMinExperienceYears,
        List<String> aiCertifications,
        AiStatus aiStatus) {
}
