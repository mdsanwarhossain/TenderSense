package com.bracit.tendersense.dto;

import com.bracit.tendersense.entity.enums.MatchGrade;

import java.time.Instant;
import java.time.LocalDateTime;

/** One row of the notification bell: a tender that newly matched this company's profile. */
public record NotificationResponse(
        Long id,
        Long tenderId,
        String tenderTitle,
        String procuringEntity,
        MatchGrade grade,
        double score,
        LocalDateTime closingAt,
        boolean read,
        Instant createdAt) {
}
