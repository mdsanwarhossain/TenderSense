package com.bracit.tendersense.controller;

import com.bracit.tendersense.config.CurrentOrganisation;
import com.bracit.tendersense.dto.BidDecisionRequest;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.BidDecision;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.exception.NotFoundException;
import com.bracit.tendersense.repository.BidDecisionRepository;
import com.bracit.tendersense.repository.TenderRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/** Records the BD team's bid / hold / skip calls. SKIPs feed the ranking loop. */
@RestController
@RequestMapping("/api/tenders/{tenderId}/decision")
@RequiredArgsConstructor
public class FeedbackController {

    private final TenderRepository tenderRepository;
    private final BidDecisionRepository decisionRepository;

    @PostMapping
    public BidDecisionRequest record(@CurrentOrganisation Organisation organisation,
                                     @PathVariable Long tenderId,
                                     @Valid @RequestBody BidDecisionRequest request) {
        Tender tender = tenderRepository.findById(tenderId)
                .orElseThrow(() -> new NotFoundException("tender " + tenderId + " not found"));

        decisionRepository.save(BidDecision.builder()
                .tender(tender)
                .organisation(organisation)
                .action(request.action())
                .note(request.note())
                .decidedBy(request.decidedBy())
                .decidedAt(Instant.now())
                .build());

        return request;
    }
}
