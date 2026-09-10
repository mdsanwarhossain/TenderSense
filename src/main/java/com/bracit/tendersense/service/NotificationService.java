package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.NotificationResponse;
import com.bracit.tendersense.dto.PageResponse;
import com.bracit.tendersense.entity.Organisation;
import org.springframework.data.domain.Pageable;

/**
 * Surfaces newly-graded matches to the company they matched: the bell in the top bar.
 *
 * <p>Creation is driven from {@link ScoringService#recalibrateGrades}, the one point
 * every scoring path -- scheduled discovery, reconcile, and a manual rescore -- funnels
 * through once grades are final. Reading and acknowledging are driven from the API.
 */
public interface NotificationService {

    /**
     * Notifies this company about any S/A-grade match it has not already been notified
     * about. Idempotent: call it as often as grades are recalculated, and it only ever
     * creates rows for matches genuinely new to the bell.
     */
    void syncNewMatches(Organisation organisation);

    PageResponse<NotificationResponse> list(Organisation organisation, boolean unreadOnly, Pageable pageable);

    long unreadCount(Organisation organisation);

    /** No-ops if the notification does not exist or belongs to another company. */
    void markRead(Organisation organisation, Long id);

    /** Returns how many were flipped, so the caller can decide whether anything changed. */
    int markAllRead(Organisation organisation);
}
