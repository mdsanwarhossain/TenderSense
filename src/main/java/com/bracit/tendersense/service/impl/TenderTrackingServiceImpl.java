package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.TrackingState;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.TenderTracking;
import com.bracit.tendersense.exception.NotFoundException;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.repository.TenderTrackingRepository;
import com.bracit.tendersense.service.TenderTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class TenderTrackingServiceImpl implements TenderTrackingService {

    private final TenderTrackingRepository trackingRepository;
    private final TenderRepository tenderRepository;

    @Override
    public TrackingState setWishlisted(Organisation organisation, Long tenderId, boolean on) {
        return update(organisation, tenderId,
                row -> row.setWishlistedAt(on ? keepOrNow(row.getWishlistedAt()) : null));
    }

    @Override
    public TrackingState setSubmitted(Organisation organisation, Long tenderId, boolean on) {
        return update(organisation, tenderId,
                row -> row.setSubmittedAt(on ? keepOrNow(row.getSubmittedAt()) : null));
    }

    @Override
    public Map<Long, TrackingState> statesFor(Organisation organisation, Collection<Long> tenderIds) {
        if (tenderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, TrackingState> out = new HashMap<>();
        for (Object[] row : trackingRepository.findStates(organisation.getId(), tenderIds)) {
            Long tenderId = (Long) row[0];
            out.put(tenderId, TrackingState.of(tenderId, (Instant) row[1], (Instant) row[2]));
        }
        return out;
    }

    /**
     * Deliberately not {@code @Transactional}: each attempt commits on its own, so the
     * retry below runs in a fresh transaction rather than one already marked for rollback.
     */
    private TrackingState update(Organisation organisation, Long tenderId,
                                 Consumer<TenderTracking> change) {
        Tender tender = tenderRepository.findById(tenderId)
                .orElseThrow(() -> new NotFoundException("tender " + tenderId + " not found"));
        try {
            return apply(organisation, tender, change);
        } catch (DataIntegrityViolationException raced) {
            // Two first-ever clicks on the same tender both tried to insert its row; the
            // unique key let one through. Apply this change on top of the winner.
            return apply(organisation, tender, change);
        }
    }

    private TrackingState apply(Organisation organisation, Tender tender,
                                Consumer<TenderTracking> change) {
        TenderTracking row = trackingRepository
                .findByTenderIdAndOrganisationId(tender.getId(), organisation.getId())
                .orElseGet(() -> TenderTracking.builder()
                        .tender(tender).organisation(organisation).build());
        change.accept(row);
        row.setUpdatedAt(Instant.now());
        TenderTracking saved = trackingRepository.saveAndFlush(row);
        return TrackingState.of(tender.getId(), saved.getWishlistedAt(), saved.getSubmittedAt());
    }

    /**
     * Turning on something already on keeps its original time.
     *
     * <p>Truncated to microseconds because that is what Postgres stores. Without it the
     * first reply echoes nanoseconds and every later read returns the rounded value, so
     * the same saved-at time would come back looking different.
     */
    private static Instant keepOrNow(Instant existing) {
        return existing != null ? existing : Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
