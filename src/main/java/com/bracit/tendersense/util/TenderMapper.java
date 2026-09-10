package com.bracit.tendersense.util;

import com.bracit.tendersense.config.EgpProperties;
import com.bracit.tendersense.config.IsdbProperties;
import com.bracit.tendersense.config.UngmProperties;
import com.bracit.tendersense.config.WorldBankProperties;
import com.bracit.tendersense.dto.TenderDetailResponse;
import com.bracit.tendersense.dto.TenderSummaryResponse;
import com.bracit.tendersense.dto.TrackingState;
import com.bracit.tendersense.entity.MatchResult;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.BidAction;
import com.bracit.tendersense.entity.enums.EligibilityStatus;
import com.bracit.tendersense.entity.enums.EligibilityVerdictView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class TenderMapper {

    /** A tender closing within this window is flagged urgent on the shortlist. */
    public static final int URGENT_DAYS = 7;

    private final EgpProperties egp;
    private final WorldBankProperties worldBank;
    private final UngmProperties ungm;
    private final IsdbProperties isdb;

    /** For callers with no tracking to show, such as the morning digest. */
    public TenderSummaryResponse toSummary(Tender t, MatchResult match, EligibilityVerdictView elig) {
        return toSummary(t, match, elig, null);
    }

    public TenderSummaryResponse toSummary(Tender t, MatchResult match, EligibilityVerdictView elig,
                                           TrackingState tracking) {
        Integer days = daysToDeadline(t.getClosingAt());
        return new TenderSummaryResponse(
                t.getId(),
                t.getExternalId(),
                t.getSourcePortal(),
                t.getTitle(),
                t.getProcuringEntity(),
                t.getProcurementNature(),
                t.getProcurementMethod(),
                t.getClosingAt(),
                days,
                days != null && days <= URGENT_DAYS && days >= 0,
                match == null ? null : match.getGrade(),
                match == null ? null : match.getScore(),
                elig == null ? null : elig.status(),
                elig == null ? 0 : elig.blockingGapCount(),
                recommend(match, elig),
                match == null ? null : match.getSummaryText(),
                tracking != null && tracking.wishlisted(),
                tracking != null && tracking.submitted(),
                tracking == null ? null : tracking.submittedAt(),
                sourceUrl(t));
    }

    public TenderDetailResponse toDetail(Tender t) {
        return new TenderDetailResponse(
                t.getId(), t.getExternalId(), t.getSourcePortal(), t.getReferenceNo(),
                t.getTitle(), t.getDescription(), t.getMinistry(), t.getDivision(),
                t.getOrganization(), t.getProcuringEntity(), t.getDistrict(), t.getCountry(),
                t.getProcurementNature(), t.getProcurementType(), t.getProcurementMethod(),
                t.getBudgetType(), t.getSourceOfFunds(), t.getDocumentPriceBdt(),
                t.getPublishedAt(), t.getClosingAt(), daysToDeadline(t.getClosingAt()),
                t.getStatus(), t.getEligibilityText(), t.getRawSnapshotPath(),
                t.getContentHash(), t.getRevisionCount());
    }

    /**
     * The tender's own page on its portal, where the team goes to read the documents and
     * submit a bid.
     *
     * <p>Built from the id already stored rather than saved per row, so if a portal moves
     * its pages it is a config change, not a data migration. Null when there is no id to
     * build from -- the UI hides the link rather than offering a broken one.
     */
    public String sourceUrl(Tender t) {
        String id = t.getExternalId();
        if (id == null || id.isBlank() || t.getSourcePortal() == null) {
            return null;
        }
        String encoded = URLEncoder.encode(id.strip(), StandardCharsets.UTF_8);
        return switch (t.getSourcePortal()) {
            // The crawler POSTs to this page, but it answers a plain GET with the same
            // tender -- which is what a link has to be.
            case EGP_BANGLADESH -> egp.getBaseUrl() + egp.getDetailPath() + "?id=" + encoded + "&h=t";
            case WORLD_BANK -> fill(worldBank.getNoticeUrl(), encoded);
            case UNGM -> fill(ungm.getNoticeUrl(), encoded);
            case ISDB -> fill(isdb.getNoticeUrl(), encoded);
        };
    }

    /** A blank template means "no verified link for this portal": hide it, don't guess. */
    private static String fill(String template, String encodedId) {
        return template == null || template.isBlank() ? null : template.replace("{id}", encodedId);
    }

    /**
     * Start of the "closing within 7 days" window. The filter and the row's amber flag
     * must be the same set, so both are defined from {@link #daysToDeadline}: a tender is
     * urgent when its closing date is today or one of the next {@link #URGENT_DAYS} days.
     */
    public static LocalDateTime urgentFrom() {
        return LocalDate.now().atStartOfDay();
    }

    /** Exclusive end of that window: the start of the day after the last urgent day. */
    public static LocalDateTime urgentUntil() {
        return LocalDate.now().plusDays(URGENT_DAYS + 1L).atStartOfDay();
    }

    public static Integer daysToDeadline(LocalDateTime closingAt) {
        if (closingAt == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), closingAt.toLocalDate());
    }

    /**
     * Bid / hold / skip. A blocking eligibility failure always wins over a strong
     * match score: there is no point recommending a tender we cannot legally enter.
     */
    public BidAction recommend(MatchResult match, EligibilityVerdictView elig) {
        if (elig != null && elig.status() == EligibilityStatus.INELIGIBLE) {
            return BidAction.SKIP;
        }
        if (match == null || match.getGrade() == null) {
            return null;
        }
        return switch (match.getGrade()) {
            case S, A -> elig != null && elig.status() == EligibilityStatus.NEEDS_VERIFICATION
                    ? BidAction.HOLD : BidAction.BID;
            case B -> BidAction.HOLD;
            case C -> BidAction.SKIP;
        };
    }
}
