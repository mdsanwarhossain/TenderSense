package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.BenchmarkResponse;
import com.bracit.tendersense.entity.MatchResult;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.MatcherType;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.repository.MatchResultRepository;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.*;

/**
 * Semantic vs keyword on the held-out labels.
 *
 * <p>Method: restrict both matchers to the held-out tenders only, rank each by its
 * stored score, take the top k, and count how many are labelled relevant. Neither
 * matcher ever sees the labels, and both rank the same candidate pool, so the only
 * thing being compared is ordering quality.
 *
 * <p>The caveat string returned with the result is not decoration. With a set this
 * small a one-hit difference is inside the noise, and the honest framing -- lead with
 * the mechanism, report the metric as supporting evidence -- belongs next to the number.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvaluationServiceImpl implements EvaluationService {

    private static final String LABELS = "data/labeled-tenders.placeholder.json";

    private final TenderRepository tenderRepository;
    private final MatchResultRepository matchResultRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private record Label(String externalId, SourcePortal source, boolean relevant, String split) {
    }

    @Override
    @Transactional(readOnly = true)
    public BenchmarkResponse benchmark() {
        List<Label> labels = loadLabels();
        List<Label> heldOut = labels.stream().filter(l -> "HELDOUT".equals(l.split())).toList();
        if (heldOut.isEmpty()) {
            return empty("No held-out labels are configured.");
        }

        int k = 5;

        // Resolve labels to stored tenders. A label whose tender is not in the corpus
        // is dropped rather than counted as a miss -- it would penalise the matchers
        // for an ingestion gap rather than for ranking badly.
        Map<Long, Label> byTenderId = new LinkedHashMap<>();
        for (Label label : heldOut) {
            tenderRepository.findBySourcePortalAndExternalId(label.source(), label.externalId())
                    .ifPresent(t -> byTenderId.put(t.getId(), label));
        }
        if (byTenderId.isEmpty()) {
            return empty("Held-out tenders are not present in the corpus; ingest before evaluating.");
        }

        List<Long> ids = new ArrayList<>(byTenderId.keySet());
        Map<Long, Integer> semanticRank = rank(MatcherType.EMBEDDING, ids);
        Map<Long, Integer> keywordRank = rank(MatcherType.KEYWORD, ids);

        long relevantTotal = byTenderId.values().stream().filter(Label::relevant).count();
        int effectiveK = (int) Math.min(k, Math.min(ids.size(), Math.max(1, relevantTotal * 2)));

        BenchmarkResponse.MatcherScore semantic =
                scoreAt(byTenderId, semanticRank, effectiveK, "EMBEDDING");
        BenchmarkResponse.MatcherScore keyword =
                scoreAt(byTenderId, keywordRank, effectiveK, "KEYWORD");

        List<BenchmarkResponse.Comparison> comparisons = new ArrayList<>();
        for (Map.Entry<Long, Label> e : byTenderId.entrySet()) {
            Tender t = tenderRepository.findById(e.getKey()).orElse(null);
            comparisons.add(new BenchmarkResponse.Comparison(
                    e.getKey(),
                    t == null ? e.getValue().externalId() : t.getTitle(),
                    e.getValue().relevant(),
                    semanticRank.get(e.getKey()),
                    keywordRank.get(e.getKey())));
        }
        comparisons.sort(Comparator.comparing(
                c -> c.semanticRank() == null ? Integer.MAX_VALUE : c.semanticRank()));

        log.info("benchmark: semantic p@{}={} keyword p@{}={} over {} held-out tenders",
                effectiveK, semantic.precisionAtK(), effectiveK, keyword.precisionAtK(), ids.size());

        return new BenchmarkResponse(ids.size(), effectiveK, semantic, keyword, comparisons,
                caveat(ids.size(), relevantTotal));
    }

    /** 1-based rank of each tender under one matcher, best score first. */
    private Map<Long, Integer> rank(MatcherType type, List<Long> ids) {
        List<MatchResult> results = new ArrayList<>();
        for (Long id : ids) {
            matchResultRepository.findByTenderIdAndMatcherType(id, type).ifPresent(results::add);
        }
        results.sort(Comparator.comparingDouble(MatchResult::getScore).reversed());

        Map<Long, Integer> out = new HashMap<>();
        for (int i = 0; i < results.size(); i++) {
            out.put(results.get(i).getTender().getId(), i + 1);
        }
        return out;
    }

    private BenchmarkResponse.MatcherScore scoreAt(Map<Long, Label> labels,
                                                   Map<Long, Integer> ranks,
                                                   int k, String matcher) {
        int hits = 0;
        for (Map.Entry<Long, Label> e : labels.entrySet()) {
            Integer rank = ranks.get(e.getKey());
            if (rank != null && rank <= k && e.getValue().relevant()) {
                hits++;
            }
        }
        return new BenchmarkResponse.MatcherScore(matcher, hits, k, (double) hits / k);
    }

    private String caveat(int heldOut, long relevant) {
        return ("Provisional: labels are placeholders assessed from tender titles, not supplied "
                + "by BracIT. With %d held-out tenders of which %d are relevant, a one-hit "
                + "difference in precision@k is within noise. Treat the ranked comparison below "
                + "as the evidence and the single number as supporting.")
                .formatted(heldOut, relevant);
    }

    private BenchmarkResponse empty(String caveat) {
        return new BenchmarkResponse(0, 5,
                new BenchmarkResponse.MatcherScore("EMBEDDING", 0, 0, 0d),
                new BenchmarkResponse.MatcherScore("KEYWORD", 0, 0, 0d),
                List.of(), caveat);
    }

    private List<Label> loadLabels() {
        try (InputStream in = new ClassPathResource(LABELS).getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            List<Label> out = new ArrayList<>();
            for (JsonNode n : root.path("labels")) {
                out.add(new Label(
                        n.path("externalId").asString(),
                        SourcePortal.valueOf(n.path("source").asString()),
                        n.path("relevant").asBoolean(),
                        n.path("split").asString("DEV")));
            }
            return out;
        } catch (Exception e) {
            log.error("could not load labels: {}", e.getMessage());
            return List.of();
        }
    }
}
