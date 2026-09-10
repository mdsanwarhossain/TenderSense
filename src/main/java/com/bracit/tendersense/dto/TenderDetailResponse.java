package com.bracit.tendersense.dto;

import com.bracit.tendersense.entity.enums.Sector;
import com.bracit.tendersense.entity.enums.SourcePortal;

import java.math.BigDecimal;
import java.time.Instant;
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
        int revisionCount,
        Sector sector,
        /** The sector as the screen shows it, e.g. "IT services". */
        String sectorLabel,
        /** The acting company's tracking of this tender -- same meaning as on the list. */
        boolean wishlisted,
        boolean submitted,
        Instant submittedAt,
        /** The tender's own page on its portal; null when no verified link exists. */
        String sourceUrl) {
}
