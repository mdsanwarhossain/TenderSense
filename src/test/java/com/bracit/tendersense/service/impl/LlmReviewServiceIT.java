package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.LlmProperties;
import com.bracit.tendersense.dto.LlmMatchVerdict;
import com.bracit.tendersense.entity.MatchResult;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.LlmReviewStatus;
import com.bracit.tendersense.entity.enums.MatcherType;
import com.bracit.tendersense.exception.LlmScoringException;
import com.bracit.tendersense.exception.LlmUnavailableException;
import com.bracit.tendersense.repository.MatchResultRepository;
import com.bracit.tendersense.repository.OrganisationRepository;
import com.bracit.tendersense.repository.PipelineRunRepository;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.service.CapabilityProfileService;
import com.bracit.tendersense.service.LlmMatchScorer;
import com.bracit.tendersense.service.LlmReviewService.Outcome;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The review job against the real database, with a stub in place of the model.
 *
 * <p>This suite shares the development database, so every test snapshots BracIT's
 * EMBEDDING rows first and restores them exactly afterwards -- a test run must never wipe
 * verdicts a real model spent minutes producing.
 */
@SpringBootTest(properties = {"tendersense.source.mode=cached", "tendersense.auth.allow-header=true"})
class LlmReviewServiceIT {

    private static final int TOP_N = 3;

    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private MatchResultRepository matchResultRepository;
    @Autowired private TenderRepository tenderRepository;
    @Autowired private CapabilityProfileService profileService;
    @Autowired private PipelineRunRepository runRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    private Organisation bracit;
    private List<Map<String, Object>> snapshot;
    private Instant started;

    /** Returns a fixed verdict, or fails as told, and counts every model call. */
    static final class StubScorer implements LlmMatchScorer {
        final AtomicInteger calls = new AtomicInteger();
        String model = "stub:v1";
        RuntimeException failWith;

        @Override
        public String modelVersion() {
            return model;
        }

        @Override
        public LlmMatchVerdict score(String systemPrompt, String tenderPrompt) {
            calls.incrementAndGet();
            if (failWith != null) {
                throw failWith;
            }
            return new LlmMatchVerdict(77, "stub verdict");
        }
    }

    @BeforeEach
    void setUp() {
        bracit = organisationRepository.findBySlug("bracit").orElse(null);
        Assumptions.assumeTrue(bracit != null
                        && matchResultRepository.countByOrganisationIdAndMatcherType(
                                bracit.getId(), MatcherType.EMBEDDING) > 0,
                "a scored corpus is required");
        started = Instant.now();
        snapshot = jdbc.queryForList("""
                select id, score, llm_score, llm_reasoning, llm_status, llm_model, llm_input_hash,
                       llm_error, llm_scored_at, llm_duration_ms
                from match_result where organisation_id = ? and matcher_type = 'EMBEDDING'
                """, bracit.getId());
        // Start every test from "never reviewed".
        jdbc.update("""
                update match_result set llm_score = null, llm_reasoning = null, llm_status = null,
                       llm_model = null, llm_input_hash = null, llm_error = null,
                       llm_scored_at = null, llm_duration_ms = null
                where organisation_id = ?""", bracit.getId());
    }

    @AfterEach
    void restore() {
        if (snapshot == null) {
            return;
        }
        jdbc.batchUpdate("""
                update match_result set score = ?, llm_score = ?, llm_reasoning = ?, llm_status = ?,
                       llm_model = ?, llm_input_hash = ?, llm_error = ?, llm_scored_at = ?,
                       llm_duration_ms = ?
                where id = ?""",
                snapshot.stream().map(r -> new Object[]{
                        r.get("score"), r.get("llm_score"), r.get("llm_reasoning"), r.get("llm_status"),
                        r.get("llm_model"), r.get("llm_input_hash"), r.get("llm_error"),
                        r.get("llm_scored_at"), r.get("llm_duration_ms"), r.get("id")}).toList());
        jdbc.update("delete from pipeline_run where job_name like 'llm-review:%' and started_at >= ?",
                Timestamp.from(started));
    }

    private LlmReviewServiceImpl service(LlmMatchScorer scorer, int topN) {
        LlmProperties props = new LlmProperties();
        props.setEnabled(true);
        props.setTopN(topN);
        return new LlmReviewServiceImpl(props, scorer, matchResultRepository, tenderRepository,
                organisationRepository, profileService, runRepository, transactionManager);
    }

    /** The same page one the service selects. */
    private List<Long> pageOne(int n) {
        return matchResultRepository.findRanked(MatcherType.EMBEDDING, bracit.getId(), null, null,
                        null, false, LocalDateTime.now(), PageRequest.of(0, n))
                .getContent().stream().map(MatchResult::getId).toList();
    }

    private MatchResult row(Long id) {
        return matchResultRepository.findById(id).orElseThrow();
    }

    @Test
    @DisplayName("scores page one, then makes zero model calls when nothing has changed")
    void skipsWhatIsUpToDate() {
        StubScorer stub = new StubScorer();
        LlmReviewServiceImpl svc = service(stub, TOP_N);

        Outcome first = svc.review(bracit);
        assertEquals(TOP_N, first.scored());
        assertEquals(TOP_N, stub.calls.get());
        for (Long id : pageOne(TOP_N)) {
            assertEquals(LlmReviewStatus.SCORED, row(id).getLlmStatus());
            assertEquals(77, row(id).getLlmScore());
        }

        Outcome second = svc.review(bracit);
        assertEquals(TOP_N, stub.calls.get(), "an unchanged page one must cost no model calls");
        assertEquals(TOP_N, second.upToDate());
        assertEquals(0, second.scored());
    }

    @Test
    @DisplayName("a changed input makes old verdicts STALE, even for rows no longer selected")
    void staleSweep() {
        StubScorer stub = new StubScorer();
        service(stub, TOP_N).review(bracit);
        List<Long> page = pageOne(TOP_N);

        stub.model = "stub:v2";                  // same effect as a profile edit or a revised tender
        service(stub, 1).review(bracit);         // re-selects only the first row

        assertEquals(LlmReviewStatus.SCORED, row(page.get(0)).getLlmStatus(), "re-reviewed");
        assertEquals(LlmReviewStatus.STALE, row(page.get(1)).getLlmStatus(), "swept, not re-reviewed");
        assertEquals(LlmReviewStatus.STALE, row(page.get(2)).getLlmStatus());
    }

    @Test
    @DisplayName("an unreachable model stops the run after one call and leaves nothing pending")
    void unreachableAborts() {
        StubScorer stub = new StubScorer();
        stub.failWith = new LlmUnavailableException("Ollama is unreachable at http://localhost:11434", null);

        Outcome outcome = service(stub, TOP_N).review(bracit);

        assertNotNull(outcome.aborted());
        assertEquals(1, stub.calls.get(), "every remaining call would fail the same way");
        Integer pending = jdbc.queryForObject(
                "select count(*) from match_result where organisation_id = ? and llm_status = 'PENDING'",
                Integer.class, bracit.getId());
        assertEquals(0, pending);
    }

    @Test
    @DisplayName("a bad answer for one tender is recorded and the run carries on")
    void perTenderFailure() {
        StubScorer stub = new StubScorer();
        stub.failWith = new LlmScoringException("no valid verdict after 2 attempts");

        Outcome outcome = service(stub, TOP_N).review(bracit);

        assertNull(outcome.aborted());
        assertEquals(TOP_N, outcome.failed());
        assertEquals(TOP_N, stub.calls.get());
        pageOne(TOP_N).forEach(id -> assertEquals(LlmReviewStatus.FAILED, row(id).getLlmStatus()));
    }

    /**
     * The race @DynamicUpdate exists for. A rescore loads a row, the LLM job commits a
     * verdict on that row, then the rescore saves its copy. Without dynamic update the save
     * writes every column -- including the llm_* nulls it loaded -- and the verdict is gone.
     */
    @Test
    @DisplayName("a rescore that overlaps the review cannot overwrite the verdict")
    void dynamicUpdateGuardsTheVerdict() {
        Long id = pageOne(1).get(0);
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate concurrent = new TransactionTemplate(transactionManager);
        concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        outer.executeWithoutResult(s -> {
            MatchResult stale = matchResultRepository.findById(id).orElseThrow();
            assertNull(stale.getLlmScore(), "precondition: loaded before any verdict");

            concurrent.executeWithoutResult(s2 -> matchResultRepository.recordLlmVerdict(
                    id, 91, "committed while the rescore held its copy", LlmReviewStatus.SCORED,
                    "stub:v1", "hash", Instant.now(), 5L));

            stale.setScore(stale.getScore() + 0.0001);   // what ScoringServiceImpl.persist() does
            matchResultRepository.save(stale);
        });

        Map<String, Object> after = jdbc.queryForMap(
                "select llm_score, llm_status from match_result where id = ?", id);
        assertEquals(91, ((Number) after.get("llm_score")).intValue(),
                "the rescore wrote back its stale copy of the llm columns");
        assertEquals("SCORED", after.get("llm_status"));
    }

    @Test
    @DisplayName("a company with no service lines is skipped, not scored against nothing")
    void emptyProfileSkipped() {
        Organisation empty = organisationRepository.findByActiveTrueOrderByIdAsc().stream()
                .filter(o -> profileService.forOrganisation(o).getServices().isEmpty())
                .findFirst().orElse(null);
        Assumptions.assumeTrue(empty != null, "needs a company with an empty profile");

        StubScorer stub = new StubScorer();
        Outcome outcome = service(stub, TOP_N).review(empty);

        assertEquals(0, stub.calls.get());
        assertEquals(0, outcome.selected());
        assertNotNull(outcome.aborted());
        jdbc.update("delete from pipeline_run where job_name = ? and started_at >= ?",
                "llm-review:" + empty.getSlug(), Timestamp.from(started));
    }
}
