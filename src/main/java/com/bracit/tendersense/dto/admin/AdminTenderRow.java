package com.bracit.tendersense.dto.admin;

import com.bracit.tendersense.entity.enums.AiStatus;
import com.bracit.tendersense.entity.enums.SourcePortal;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * One tender as the admin corpus list shows it: what was collected, from where, and what
 * the local model made of it.
 *
 * <p>No score, no grade, no saved or submitted marks. Those belong to a company -- an
 * admin account has none, and the same tender scores differently for each company, so
 * showing one company's numbers here would be a lie of omission.
 */
public record AdminTenderRow(Long id,
                             SourcePortal source,
                             String externalId,
                             String referenceNo,
                             /** The model's shorter title where there is one, else the portal's. */
                             String title,
                             /** True when the title above came from the model. */
                             boolean shortened,
                             String buyer,
                             String sector,
                             String category,
                             String noticeType,
                             LocalDateTime publishedAt,
                             LocalDateTime closingAt,
                             boolean closed,
                             AiStatus aiStatus,
                             Instant aiProcessedAt,
                             Instant firstSeenAt,
                             /** The tender's own page on its portal; null when it cannot be built. */
                             String sourceUrl) {
}
