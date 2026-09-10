package com.bracit.tendersense.controller;

import com.bracit.tendersense.config.CurrentOrganisation;
import com.bracit.tendersense.dto.TrackingState;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.service.TenderTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Save for later, and mark as submitted on the portal.
 *
 * <p>PUT sets, DELETE clears -- not a single toggle endpoint. With a toggle, a
 * double-click or a retried request flips the state straight back; with PUT/DELETE,
 * sending the same request twice lands in the same place.
 */
@RestController
@RequestMapping("/api/tenders/{tenderId}")
@RequiredArgsConstructor
public class TrackingController {

    private final TenderTrackingService trackingService;

    @PutMapping("/wishlist")
    public TrackingState save(@CurrentOrganisation Organisation organisation,
                              @PathVariable Long tenderId) {
        return trackingService.setWishlisted(organisation, tenderId, true);
    }

    @DeleteMapping("/wishlist")
    public TrackingState unsave(@CurrentOrganisation Organisation organisation,
                                @PathVariable Long tenderId) {
        return trackingService.setWishlisted(organisation, tenderId, false);
    }

    @PutMapping("/submission")
    public TrackingState markSubmitted(@CurrentOrganisation Organisation organisation,
                                       @PathVariable Long tenderId) {
        return trackingService.setSubmitted(organisation, tenderId, true);
    }

    @DeleteMapping("/submission")
    public TrackingState unmarkSubmitted(@CurrentOrganisation Organisation organisation,
                                         @PathVariable Long tenderId) {
        return trackingService.setSubmitted(organisation, tenderId, false);
    }
}
