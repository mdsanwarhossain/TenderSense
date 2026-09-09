package com.bracit.tendersense.dto;

import com.bracit.tendersense.entity.enums.SourcePortal;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        int revisionCount) {
}
