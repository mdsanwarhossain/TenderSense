package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.LlmProperties;
import com.bracit.tendersense.dto.MatchComparison;
import com.bracit.tendersense.dto.MatchEvidenceResponse;
import com.bracit.tendersense.dto.MatchSummaryResponse;
import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.MatchResult;
import com.bracit.tendersense.entity.MatchSummary;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.MatcherType;
import com.bracit.tendersense.repository.MatchResultRepository;
import com.bracit.tendersense.repository.MatchSummaryRepository;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.service.CapabilityProfileService;
import com.bracit.tendersense.service.LlmClient;
import com.bracit.tendersense.service.MatchSummaryService;
import com.bracit.tendersense.service.ModelPriority;
import com.bracit.tendersense.util.MatchComparisonValidator;
import com.bracit.tendersense.util.MatchSummaryPrompt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Writes and caches the tender-against-profile comparison.
 *
 * <p>Three things keep a reader from waiting on the model twice: the day's cache, an
 * in-flight set so two people opening the same tender generate it once, and a short
 * cool-off after a failure so a polling page cannot hammer a model that is not answering.
 *
 * <p>A comparison that has aged out is still served while its replacement is written --
 * yesterday's reading of the same tender is better than a spinner.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchSummaryServiceImpl implements MatchSummaryService {

    /** How long to leave the model alone after it failed on a tender. */
    private static final long FAILURE_COOL_OFF_MINUTES = 10;

    /** One retry, with a different seed: the usual failure is a malformed JSON reply. */
    private static final int MAX_ATTEMPTS = 2;

    private static final String NOT_SCORED = "This tender has not been matched against your profile yet.";

    private final MatchSummaryRepository summaryRepository;
    private final MatchResultRepository matchResultRepository;
    private final TenderRepository tenderRepository;
    private final CapabilityProfileService profileService;
    private final LlmClient llmClient;
    private final LlmProperties llm;
    private final ModelPriority modelPriority;
    private final Executor matchSummaryExecutor;

    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Boolean> inFlight = new ConcurrentHashMap<>();
    private final Map<String, Instant> failedAt = new ConcurrentHashMap<>();

    /**
     * Transactional because the prompt reads the profile's services, past projects and
     * certifications, which are lazy. Building it here also initialises them, so the
     * detached entities handed to the background thread are safe to read there.
     */
    @Override
    @Transactional(readOnly = true)
    public MatchSummaryResponse forTender(Organisation organisation, Long tenderId) {
        Tender tender = tenderRepository.findById(tenderId).orElse(null);
        if (tender == null) {
            return MatchSummaryResponse.unavailable(NOT_SCORED);
        }

        Optional<MatchResult> match = matchResultRepository
                .findByTenderIdAndOrganisationIdAndMatcherType(tenderId, organisation.getId(), MatcherType.EMBEDDING);
        // Nothing to compare against: the matcher has not seen this tender for this company.
        if (match.isEmpty()) {
            return MatchSummaryResponse.unavailable(NOT_SCORED);
        }
        String fallback = match.get().getSummaryText() == null || match.get().getSummaryText().isBlank()
                ? NOT_SCORED : match.get().getSummaryText();

        CapabilityProfile profile = profileService.forOrganisation(organisation);
        List<MatchEvidenceResponse.EvidencePair> evidence = readEvidence(match.get().getEvidenceJson());
        String prompt = MatchSummaryPrompt.user(tender, profile, evidence);
        String hash = sha256(prompt);

        MatchSummary cached = summaryRepository
                .findByTenderIdAndOrganisationId(tenderId, organisation.getId()).orElse(null);
        if (fresh(cached, hash)) {
            return ready(cached);
        }

        if (!llm.isEnabled()) {
            // No model on this instance: the written sentence the matcher stored is all there is.
            return cached != null ? ready(cached) : MatchSummaryResponse.unavailable(fallback);
        }

        String key = tenderId + ":" + organisation.getId();
        if (coolingOff(key)) {
            return cached != null ? ready(cached) : MatchSummaryResponse.unavailable(fallback);
        }
        // Read the answer before handing the work off, so it describes the state this
        // reader actually saw: a stale comparison beats a spinner, and only a first-ever
        // read waits. Once generation is running, the row underneath is being replaced.
        MatchSummaryResponse answer = cached != null ? ready(cached) : MatchSummaryResponse.generating();
        start(key, tender, profile, evidence, hash, organisation);
        return answer;
    }

    /** Submits the model call unless this pair is already being written. */
    private void start(String key, Tender tender, CapabilityProfile profile,
                       List<MatchEvidenceResponse.EvidencePair> evidence, String hash,
                       Organisation organisation) {
        if (inFlight.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        try {
            matchSummaryExecutor.execute(() -> {
                try {
                    generate(tender, profile, evidence, hash, organisation);
                } finally {
                    inFlight.remove(key);
                }
            });
        } catch (RejectedExecutionException e) {
            // More readers than the queue holds. They poll; the next one gets a slot.
            inFlight.remove(key);
            log.debug("comparison for {} not queued: {}", key, e.getMessage());
        }
    }

    /**
     * Runs off the request thread and outside any transaction: the model call takes
     * seconds, and holding a database connection open across it would be worse than
     * the wait. Only the write at the end touches the database.
     */
    private void generate(Tender tender, CapabilityProfile profile,
                          List<MatchEvidenceResponse.EvidencePair> evidence, String hash,
                          Organisation organisation) {
        String key = tender.getId() + ":" + organisation.getId();
        long started = System.currentTimeMillis();
        modelPriority.started();
        try {
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                try {
                    MatchComparison c = llmClient.compare(tender, profile, evidence, attempt);
                    if (c.comparison() == null || c.comparison().isBlank()) {
                        throw new IllegalStateException("the model returned an empty comparison");
                    }
                    // What the prompt asks for and what a 3B returns are not the same thing:
                    // a line that breaks the rules is dropped rather than shown.
                    MatchComparisonValidator.Result checked =
                            MatchComparisonValidator.validate(c, tender, profile);
                    if (!checked.dropped().isEmpty()) {
                        log.debug("dropped {} line(s) from the comparison for {}: {}",
                                checked.dropped().size(), key, checked.dropped());
                    }
                    save(tender, organisation, checked.kept(), hash, System.currentTimeMillis() - started);
                    failedAt.remove(key);
                    return;
                } catch (RuntimeException e) {
                    if (attempt == MAX_ATTEMPTS - 1) {
                        throw e;
                    }
                    log.debug("comparison for {} failed on attempt {}: {}", key, attempt + 1, e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            failedAt.put(key, Instant.now());
            log.warn("could not write the comparison for tender {} and {}: {}",
                    tender.getExternalId(), organisation.getSlug(), e.getMessage());
        } finally {
            modelPriority.done();
        }
    }

    /**
     * Runs on the background thread with no transaction of its own: the repository call
     * opens one, which is all a single row needs. The tender and organisation are the
     * detached entities from the request -- only their ids are written, and nothing
     * cascades from here.
     */
    private void save(Tender tender, Organisation organisation, MatchComparison c,
                      String hash, long durationMs) {
        MatchSummary row = summaryRepository
                .findByTenderIdAndOrganisationId(tender.getId(), organisation.getId())
                .orElseGet(() -> MatchSummary.builder()
                        .tender(tender)
                        .organisation(organisation)
                        .build());
        row.setComparison(c.comparison().strip());
        row.setMatches(lines(c.matches()));
        row.setGaps(lines(c.gaps()));
        row.setModel(llmClient.summaryModel());
        row.setPromptVersion(MatchSummaryPrompt.VERSION);
        row.setInputHash(hash);
        row.setGeneratedAt(Instant.now());
        row.setDurationMs(durationMs);
        summaryRepository.save(row);
    }

    /**
     * A cached comparison counts only if it is within the day AND was written from the
     * same prompt by the current model: a re-read tender or an edited profile is a
     * different question, whatever the clock says.
     */
    private boolean fresh(MatchSummary s, String hash) {
        return s != null
                && s.getGeneratedAt() != null
                && s.getGeneratedAt().isAfter(Instant.now().minus(llm.getSummaryCacheHours(), ChronoUnit.HOURS))
                && hash.equals(s.getInputHash())
                && MatchSummaryPrompt.VERSION.equals(s.getPromptVersion())
                && llmClient.summaryModel().equals(s.getModel());
    }

    private boolean coolingOff(String key) {
        Instant failed = failedAt.get(key);
        if (failed == null) {
            return false;
        }
        if (failed.isBefore(Instant.now().minus(FAILURE_COOL_OFF_MINUTES, ChronoUnit.MINUTES))) {
            failedAt.remove(key);
            return false;
        }
        return true;
    }

    private MatchSummaryResponse ready(MatchSummary s) {
        return new MatchSummaryResponse(MatchSummaryResponse.Status.READY, s.getComparison(),
                list(s.getMatches()), list(s.getGaps()), s.getModel(), s.getGeneratedAt());
    }

    /** Blank and repeated lines are the model's, not the reader's, problem. */
    private static String[] lines(List<String> values) {
        if (values == null) {
            return new String[0];
        }
        return new LinkedHashSet<>(values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::strip)
                .toList()).toArray(String[]::new);
    }

    private static List<String> list(String[] values) {
        return values == null ? List.of() : Arrays.asList(values);
    }

    private List<MatchEvidenceResponse.EvidencePair> readEvidence(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(stored, new TypeReference<List<MatchEvidenceResponse.EvidencePair>>() {
            });
        } catch (RuntimeException e) {
            log.debug("could not read stored evidence: {}", e.getMessage());
            return List.of();
        }
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }
}
