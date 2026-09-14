package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.TrackingState;
import com.bracit.tendersense.entity.Organisation;

import java.util.Collection;
import java.util.Map;

/**
 * Saved-for-later and submitted-on-the-portal, per tender per company.
 *
 * <p>Setters are idempotent rather than toggles: turning something on that is already on
 * keeps its original timestamp, so a double-click or a retried request can never flip
 * the state back, and "submitted on 12 Sep" stays 12 Sep.
 */
public interface TenderTrackingService {

    /** @throws com.bracit.tendersense.exception.NotFoundException unknown tender */
    TrackingState setWishlisted(Organisation organisation, Long tenderId, boolean on);

    /** @throws com.bracit.tendersense.exception.NotFoundException unknown tender */
    TrackingState setSubmitted(Organisation organisation, Long tenderId, boolean on);

    /** States for a page of tenders in one query. Tenders never tracked are absent. */
    Map<Long, TrackingState> statesFor(Organisation organisation, Collection<Long> tenderIds);
}
