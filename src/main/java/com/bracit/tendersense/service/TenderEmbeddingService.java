package com.bracit.tendersense.service;

import com.bracit.tendersense.entity.Tender;

import java.util.List;
import java.util.Map;

/**
 * Owns the one embedding each tender gets.
 *
 * <p>A tender's vector is a property of the tender, not of who is scoring it, so it is
 * computed once and shared by every organisation. This is what makes multi-tenant
 * scoring cheap: the second company costs cosine arithmetic, not another 70 ms per tender.
 */
public interface TenderEmbeddingService {

    /** Identifies the model, so a change invalidates stored vectors. */
    String modelVersion();

    /** Embeds any tender in the batch that lacks a current vector. Returns how many were computed. */
    int ensureEmbedded(List<Tender> tenders);

    /** Embeds everything in the corpus that needs it. Returns how many were computed. */
    int embedAll();

    /** Stored vectors for the given tenders, keyed by tender id. Missing vectors are omitted. */
    Map<Long, float[]> vectorsFor(List<Tender> tenders);
}
