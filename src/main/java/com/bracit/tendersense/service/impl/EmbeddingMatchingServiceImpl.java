package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.ScoredMatch;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.MatcherType;
import com.bracit.tendersense.service.CapabilityProfileService;
import com.bracit.tendersense.service.MatchingService;
import com.bracit.tendersense.service.TenderEmbeddingService;
import com.bracit.tendersense.util.Vectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Semantic matching against BracIT's capability profile.
 *
 * <p>The profile is embedded as individual capability statements rather than one
 * blob, for two reasons: a single averaged vector washes out specialist strengths,
 * and per-statement scores are what let the UI say <em>which</em> capability matched.
 *
 * <p>Runs entirely locally on an ONNX model -- no API key, no network at inference
 * time, which is also why the demo cannot be broken by venue connectivity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingMatchingServiceImpl implements MatchingService {

    /**
     * The single best-matching capability dominates, but a tender that echoes
     * several of our strengths should outrank one that echoes exactly one. 60/40
     * between peak and breadth; calibration then maps the result onto grades.
     */
    private static final double PEAK_WEIGHT = 0.60;
    private static final double BREADTH_WEIGHT = 0.40;
    private static final int BREADTH_TOP_N = 3;
    private static final int EVIDENCE_COUNT = 3;

    /** Tender text is truncated before embedding: the model has a token limit. */
    private static final int MAX_TENDER_CHARS = 2000;

    /**
     * How hard an excluded-work resemblance pulls a score down.
     *
     * <p>The penalty is a <em>margin</em>: it only applies when the tender matches an
     * exclusion better than it matches anything we do. Subtracting raw exclusion
     * similarity instead would shift every score down uniformly and change no ranking,
     * because near-neighbours in this embedding space score in the same narrow band.
     */
    private static final double EXCLUSION_WEIGHT = 1.0;

    private final EmbeddingModel embeddingModel;
    private final CapabilityProfileService profileService;
    private final TenderEmbeddingService tenderEmbeddingService;

    /** One profile's embedded statements. Immutable once built. */
    private record ProfileVectors(List<String> statements,
                                  List<float[]> statementVectors,
                                  List<String> exclusions,
                                  List<float[]> exclusionVectors) {
    }

    /**
     * Cached per organisation. This used to be four instance fields holding a single
     * profile, which is what made the service single-tenant — embedding a profile costs
     * ~2 seconds, so it is cached, but it must be cached per company.
     */
    private final Map<Long, ProfileVectors> byOrganisation = new ConcurrentHashMap<>();

    @Override
    public MatcherType type() {
        return MatcherType.EMBEDDING;
    }

    @Override
    public String modelVersion() {
        return "onnx:all-MiniLM-L6-v2:384+excl";
    }

    @Override
    public Map<Long, ScoredMatch> scoreAll(Organisation organisation, List<Tender> tenders) {
        ProfileVectors profile = profileFor(organisation);
        if (profile.statementVectors().isEmpty()) {
            log.warn("{} has no capability statements - every score will be zero",
                    organisation.getSlug());
            return Map.of();
        }

        // Vectors are computed once per tender and shared across organisations; this
        // only fills gaps (a new tender, or a model change).
        tenderEmbeddingService.ensureEmbedded(tenders);
        Map<Long, float[]> vectors = tenderEmbeddingService.vectorsFor(tenders);

        Map<Long, ScoredMatch> out = new LinkedHashMap<>();
        for (Tender tender : tenders) {
            float[] vector = vectors.get(tender.getId());
            if (vector == null || vector.length == 0) {
                out.put(tender.getId(), ScoredMatch.zero());
                continue;
            }
            out.put(tender.getId(), scoreOne(profile, tenderText(tender), vector));
        }
        return out;
    }

    private ScoredMatch scoreOne(ProfileVectors profile, String tenderText, float[] tenderVector) {
        List<String> statements = profile.statements();
        List<ScoredMatch.Evidence> ranked = new ArrayList<>(statements.size());
        for (int i = 0; i < statements.size(); i++) {
            double sim = Vectors.clamp01(
                    Vectors.cosine(tenderVector, profile.statementVectors().get(i)));
            ranked.add(new ScoredMatch.Evidence(statements.get(i), snippet(tenderText), sim));
        }
        ranked.sort(Comparator.comparingDouble(ScoredMatch.Evidence::similarity).reversed());

        double peak = ranked.get(0).similarity();
        double breadth = ranked.stream()
                .limit(BREADTH_TOP_N)
                .mapToDouble(ScoredMatch.Evidence::similarity)
                .average()
                .orElse(0d);

        double positive = Vectors.clamp01(PEAK_WEIGHT * peak + BREADTH_WEIGHT * breadth);
        List<ScoredMatch.Evidence> top =
                ranked.subList(0, Math.min(EVIDENCE_COUNT, ranked.size()));

        // Does this look more like work we have excluded than like work we do?
        String worstText = null;
        double worstSim = 0d;
        for (int i = 0; i < profile.exclusions().size(); i++) {
            double sim = Vectors.clamp01(
                    Vectors.cosine(tenderVector, profile.exclusionVectors().get(i)));
            if (sim > worstSim) {
                worstSim = sim;
                worstText = profile.exclusions().get(i);
            }
        }

        double margin = Math.max(0d, worstSim - peak);
        if (margin <= 0d || worstText == null) {
            return new ScoredMatch(positive, top, null);
        }

        double penalty = EXCLUSION_WEIGHT * margin;
        return new ScoredMatch(
                Vectors.clamp01(positive - penalty),
                top,
                new ScoredMatch.Exclusion(worstText, worstSim, penalty));
    }

    /**
     * Embeds one organisation's profile, once. Profile edits require an
     * {@link #invalidate(Organisation)} or a restart, which is also when that
     * organisation's stored scores must be recomputed.
     */
    private ProfileVectors profileFor(Organisation organisation) {
        return byOrganisation.computeIfAbsent(organisation.getId(), id -> {
            List<String> loaded = profileService.capabilityStatements(organisation);
            List<float[]> vectors = new ArrayList<>(loaded.size());
            for (String s : loaded) {
                vectors.add(embeddingModel.embed(s));
            }
            List<String> excl = profileService.exclusionStatements(organisation);
            List<float[]> exclVectors = new ArrayList<>(excl.size());
            for (String s : excl) {
                exclVectors.add(embeddingModel.embed(s));
            }
            log.info("embedded {} capability statements and {} exclusions for {}",
                    loaded.size(), excl.size(), organisation.getSlug());
            return new ProfileVectors(List.copyOf(loaded), List.copyOf(vectors),
                    List.copyOf(excl), List.copyOf(exclVectors));
        });
    }

    public void invalidate(Organisation organisation) {
        byOrganisation.remove(organisation.getId());
    }

    public void invalidateAll() {
        byOrganisation.clear();
    }

    private static String tenderText(Tender t) {
        StringBuilder sb = new StringBuilder();
        if (t.getTitle() != null) {
            sb.append(t.getTitle());
        }
        if (t.getDescription() != null && !t.getDescription().isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(". ");
            }
            sb.append(t.getDescription());
        }
        String text = sb.toString().trim();
        return text.length() > MAX_TENDER_CHARS ? text.substring(0, MAX_TENDER_CHARS) : text;
    }

    private static String snippet(String text) {
        return text.length() <= 240 ? text : text.substring(0, 240) + "...";
    }
}
