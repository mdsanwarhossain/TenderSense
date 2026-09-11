package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.LlmProperties;
import com.bracit.tendersense.config.ProcessingProperties;
import com.bracit.tendersense.dto.TenderEnrichment;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.TenderStaging;
import com.bracit.tendersense.entity.enums.AiStatus;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.entity.enums.StagingStatus;
import com.bracit.tendersense.exception.LlmCallException;
import com.bracit.tendersense.exception.LlmUnavailableException;
import com.bracit.tendersense.repository.OrganisationRepository;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.repository.TenderStagingRepository;
import com.bracit.tendersense.service.LlmClient;
import com.bracit.tendersense.service.ScoringService;
import com.bracit.tendersense.service.TenderIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The worker's rules, without a database: what happens when the model is down, answers
 * badly, or has already read a tender. No Spring context, so it cannot touch the live
 * staging queue in the shared development database.
 */
class TenderProcessingServiceImplTest {

    private static final String LONG_TITLE = "ADP-2024-2025-W-02 (A), Palisading of the Road at Kaurikhara "
            + "Speed Boat Ghate under Rangabali Upazila, Dist: Patuakhali (memo No-1387 Date:02 July,25 "
            + "Sl No- Sch-02) under the Upazila Parishad development fund for the current fiscal year";

    private final TenderStagingRepository staging = mock(TenderStagingRepository.class);
    private final TenderRepository tenders = mock(TenderRepository.class);
    private final TenderIngestionService ingestion = mock(TenderIngestionService.class);
    private final ScoringService scoring = mock(ScoringService.class);
    private final OrganisationRepository organisations = mock(OrganisationRepository.class);
    private final LlmClient model = mock(LlmClient.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final ObjectMapper json = new ObjectMapper();

    private TenderProcessingServiceImpl worker;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        worker = new TenderProcessingServiceImpl(staging, tenders, ingestion, scoring, organisations,
                model, new LlmProperties(), new ProcessingProperties(), tx);
        when(model.model()).thenReturn("test-model");
        when(tx.execute(any())).thenAnswer(i -> ((TransactionCallback<Object>) i.getArgument(0)).doInTransaction(null));
        doAnswer(i -> {
            ((Consumer<TransactionStatus>) i.getArgument(0)).accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
        when(staging.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(staging.save(any())).thenAnswer(i -> i.getArgument(0));
        when(staging.lockClosed(any(), anyInt())).thenReturn(List.of());
        when(staging.lockOpen(any(), anyInt())).thenReturn(List.of());
        when(tenders.findBySourcePortalAndExternalId(any(), any())).thenReturn(Optional.empty());
        when(ingestion.persist(any())).thenAnswer(i -> {
            Tender t = i.getArgument(0);
            t.setId(42L);
            return new TenderIngestionService.PersistOutcome(t, true, true);
        });
    }

    private Tender tender(LocalDateTime closing) {
        return Tender.builder().sourcePortal(SourcePortal.EGP_BANGLADESH).externalId("990001")
                .title(LONG_TITLE).description("Palisading works on the road bank.")
                .contentHash("h1").parserVersion("egp-4").closingAt(closing)
                .firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).build();
    }

    private TenderStaging row(Tender t) {
        return TenderStaging.builder().sourcePortal(t.getSourcePortal()).externalId(t.getExternalId())
                .contentHash(t.getContentHash()).parserVersion(t.getParserVersion()).closingAt(t.getClosingAt())
                .parsedJson(json.writeValueAsString(t)).fetchedAt(Instant.now()).build();
    }

    private Tender persisted() {
        ArgumentCaptor<Tender> saved = ArgumentCaptor.forClass(Tender.class);
        verify(ingestion).persist(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("model down: the tender goes back in the queue untouched, and no attempt is used up")
    void modelDown() {
        TenderStaging r = row(tender(LocalDateTime.now().plusDays(5)));
        when(staging.lockOpen(any(), anyInt())).thenReturn(List.of(r));
        when(model.enrich(any(), anyInt())).thenThrow(new LlmUnavailableException("Ollama is not reachable", null));

        assertEquals(0, worker.processBatch(true));
        assertEquals(StagingStatus.PENDING, r.getStatus());
        assertEquals(0, r.getAttempts());
        verify(ingestion, never()).persist(any());
        assertEquals("Ollama is not reachable", worker.status().lastError());
    }

    @Test
    @DisplayName("once the model answers again, the old 'not reachable' problem is no longer reported")
    void recoveryClearsTheProblem() {
        TenderStaging r = row(tender(LocalDateTime.now().plusDays(5)));
        when(staging.lockOpen(any(), anyInt())).thenReturn(List.of(r));
        when(model.enrich(any(), anyInt()))
                .thenThrow(new LlmUnavailableException("Ollama is not reachable", null))
                .thenReturn(new TenderEnrichment(null, "The buyer wants the road bank protected with palisading.",
                        List.of(), null, null, null, List.of()));

        worker.processBatch(true);
        assertEquals("Ollama is not reachable", worker.status().lastError());

        worker.processBatch(true);
        assertNull(worker.status().lastError());
        assertNull(worker.status().lastErrorAt());
    }

    @Test
    @DisplayName("two unusable answers: the tender goes live without AI rather than block the queue")
    void badAnswerTwice() {
        TenderStaging r = row(tender(LocalDateTime.now().plusDays(5)));
        when(staging.lockOpen(any(), anyInt())).thenReturn(List.of(r));
        when(model.enrich(any(), anyInt())).thenThrow(new LlmCallException("reply is not the JSON asked for"));

        assertEquals(0, worker.processBatch(true));
        assertEquals(StagingStatus.PENDING, r.getStatus(), "first failure: try again later");
        assertEquals(1, r.getAttempts());

        assertEquals(1, worker.processBatch(true));
        assertEquals(StagingStatus.PERSISTED, r.getStatus());
        assertEquals(AiStatus.FAILED, persisted().getAiStatus());
    }

    @Test
    @DisplayName("a reading already stored for this exact content, model and prompt is not redone")
    void noSecondCall() {
        Tender t = tender(LocalDateTime.now().plusDays(5));
        Tender stored = tender(t.getClosingAt());
        stored.setAiStatus(AiStatus.DONE);
        stored.setAiSummary("Palisading along the road bank.");
        stored.setAiInputHash(TenderStagingServiceImpl.aiInputHash(t, "test-model"));
        when(tenders.findBySourcePortalAndExternalId(any(), any())).thenReturn(Optional.of(stored));
        when(staging.lockOpen(any(), anyInt())).thenReturn(List.of(row(t)));

        worker.processBatch(true);
        verify(model, never()).enrich(any(), anyInt());
        assertEquals("Palisading along the road bank.", persisted().getAiSummary());
    }

    @Test
    @DisplayName("a closed tender never reaches the model")
    void closedSkipsModel() {
        TenderStaging r = row(tender(LocalDateTime.now().minusDays(3)));
        when(staging.lockClosed(any(), anyInt())).thenReturn(List.of(r));

        worker.processBatch(true);
        verify(model, never()).enrich(any(), anyInt());
        assertEquals(AiStatus.SKIPPED, persisted().getAiStatus());
        assertNull(persisted().getAiSummary());
    }

    @Test
    @DisplayName("the model's answer is checked before it is stored: invented title words are dropped")
    void answerIsValidated() {
        TenderStaging r = row(tender(LocalDateTime.now().plusDays(5)));
        when(staging.lockOpen(any(), anyInt())).thenReturn(List.of(r));
        when(model.enrich(any(), anyInt())).thenReturn(new TenderEnrichment(
                "Palisading and Road Construction at Speed Boat Ghate, Patuakhali",
                "The buyer wants the road bank at the speed boat ghat protected with palisading.",
                List.of("Palisading along the road"), "Patuakhali", null, null, List.of()));

        assertEquals(1, worker.processBatch(true));
        Tender saved = persisted();
        assertEquals(AiStatus.DONE, saved.getAiStatus());
        assertNull(saved.getAiShortTitle(), "'construction' is not in the tender");
        assertNotNull(saved.getAiSummary());
        assertEquals("Patuakhali", saved.getAiLocation());
        assertTrue(r.getAiUsed());
        assertEquals(StagingStatus.PERSISTED, r.getStatus());
    }
}
