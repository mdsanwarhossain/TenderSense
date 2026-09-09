package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.ScoredMatch;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.MatcherType;
import com.bracit.tendersense.service.CapabilityProfileService;
import com.bracit.tendersense.service.MatchingService;
import com.bracit.tendersense.util.Vectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.*;

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

    private final EmbeddingModel embeddingModel;
    private final CapabilityProfileService profileService;

    private List<String> statements = List.of();
    private List<float[]> statementVectors = List.of();

    @Override
    public MatcherType type() {
        return MatcherType.EMBEDDING;
    }

    @Override
    public String modelVersion() {
        return "onnx:all-MiniLM-L6-v2:384";
    }

    @Override
    public Map<Long, ScoredMatch> scoreAll(List<Tender> tenders) {
        ensureProfileEmbedded();
        if (statementVectors.isEmpty()) {
            log.warn("capability profile has no statements - every score will be zero");
            return Map.of();
        }

        Map<Long, ScoredMatch> out = new LinkedHashMap<>();
        for (Tender tender : tenders) {
            String text = tenderText(tender);
            if (text.isBlank()) {
                out.put(tender.getId(), ScoredMatch.zero());
                continue;
            }
            out.put(tender.getId(), scoreOne(text, embeddingModel.embed(text)));
        }
        return out;
    }

    private ScoredMatch scoreOne(String tenderText, float[] tenderVector) {
        List<ScoredMatch.Evidence> ranked = new ArrayList<>(statements.size());
        for (int i = 0; i < statements.size(); i++) {
            double sim = Vectors.clamp01(Vectors.cosine(tenderVector, statementVectors.get(i)));
            ranked.add(new ScoredMatch.Evidence(statements.get(i), snippet(tenderText), sim));
        }
        ranked.sort(Comparator.comparingDouble(ScoredMatch.Evidence::similarity).reversed());

        double peak = ranked.get(0).similarity();
        double breadth = ranked.stream()
                .limit(BREADTH_TOP_N)
                .mapToDouble(ScoredMatch.Evidence::similarity)
                .average()
                .orElse(0d);

        double score = Vectors.clamp01(PEAK_WEIGHT * peak + BREADTH_WEIGHT * breadth);
        return new ScoredMatch(score, ranked.subList(0, Math.min(EVIDENCE_COUNT, ranked.size())));
    }

    /**
     * Embeds the profile once per process. Profile edits require a restart or an
     * explicit {@link #invalidate()}, which is also when stored scores must be
     * recomputed -- both are recorded as a re-score trigger.
     */
    private synchronized void ensureProfileEmbedded() {
        if (!statementVectors.isEmpty()) {
            return;
        }
        List<String> loaded = profileService.capabilityStatements();
        if (loaded.isEmpty()) {
            return;
        }
        List<float[]> vectors = new ArrayList<>(loaded.size());
        for (String s : loaded) {
            vectors.add(embeddingModel.embed(s));
        }
        statements = List.copyOf(loaded);
        statementVectors = List.copyOf(vectors);
        log.info("embedded {} capability statements with {}", statements.size(), modelVersion());
    }

    public synchronized void invalidate() {
        statements = List.of();
        statementVectors = List.of();
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
