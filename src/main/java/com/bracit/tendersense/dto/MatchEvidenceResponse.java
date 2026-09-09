package com.bracit.tendersense.dto;

import com.bracit.tendersense.entity.enums.MatchGrade;

import java.util.List;

/**
 * Why a tender matched, in reviewable form: the contributing capability/tender
 * text pairs and their individual scores, not just a final number.
 */
public record MatchEvidenceResponse(
        Long tenderId,
        MatchGrade grade,
        double score,
        String modelVersion,
        String summary,
        com.bracit.tendersense.entity.enums.BidAction recommendation,
        /** Set when the score was pulled down for resembling excluded work. */
        String demotedFor,
        Double demotionPenalty,
        List<EvidencePair> evidence) {

    public record EvidencePair(String profileText, String tenderText, double similarity) {
    }
}
