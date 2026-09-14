package com.bracit.tendersense.dto;

import com.bracit.tendersense.entity.enums.SourcePortal;

import java.time.Instant;
import java.util.List;

/**
 * The company dashboard in one round trip.
 *
 * @param bestMatches the top of the ranked list: the same rows, in the same order
 * @param closingSoon tenders closing within seven days that are worth acting on --
 *                    saved, or graded S or A -- soonest first
 */
public record DashboardResponse(Numbers numbers,
                                List<TenderSummaryResponse> bestMatches,
                                List<TenderSummaryResponse> closingSoon,
                                Activity activity) {

    /** Every count equals what the tender list shows under the matching filter. */
    public record Numbers(long open, long sGrade, long aGrade, long closingSoon, long saved, long submitted) {
    }

    /**
     * @param unread     unread new-match alerts
     * @param newMatches the latest new-match alerts, read or not
     * @param tracking   the latest saves and submissions
     * @param scoring    whether the scores still reflect the profile
     */
    public record Activity(long unread,
                           List<NotificationResponse> newMatches,
                           List<TrackingChange> tracking,
                           ProfileStaleness scoring) {
    }

    public record TrackingChange(Long tenderId, String title, SourcePortal source,
                                 boolean saved, boolean submitted, Instant updatedAt) {
    }
}
