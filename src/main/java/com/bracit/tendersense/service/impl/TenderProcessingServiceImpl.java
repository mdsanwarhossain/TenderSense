package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.LlmProperties;
import com.bracit.tendersense.config.ProcessingProperties;
import com.bracit.tendersense.dto.ProcessingStatusResponse;
import com.bracit.tendersense.dto.TenderEnrichment;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.TenderStaging;
import com.bracit.tendersense.entity.enums.AiStatus;
import com.bracit.tendersense.entity.enums.StagingStatus;
import com.bracit.tendersense.exception.LlmCallException;
import com.bracit.tendersense.exception.LlmUnavailableException;
import com.bracit.tendersense.repository.OrganisationRepository;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.repository.TenderStagingRepository;
import com.bracit.tendersense.service.LlmClient;
import com.bracit.tendersense.service.ScoringService;
import com.bracit.tendersense.service.TenderIngestionService;
import com.bracit.tendersense.service.TenderProcessingService;
import com.bracit.tendersense.util.EnrichmentPrompt;
import com.bracit.tendersense.util.EnrichmentValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The worker between the staging table and the tender table.
 *
 * <p>Per tender: rules first (sector, standard form), then -- for open tenders that are
 * real bidding opportunities -- one call to the local model, validated field by field,
 * then the tender table. Scoring happens separately, under the pipeline lock, so the
 * model's minutes never hold up a collection run or a rescore.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderProcessingServiceImpl implements TenderProcessingService {

    private final TenderStagingRepository stagingRepository;
    private final TenderRepository tenderRepository;
    private final TenderIngestionService ingestionService;
    private final ScoringService scoringService;
    private final OrganisationRepository organisationRepository;
    private final LlmClient llmClient;
    private final LlmProperties llm;
    private final ProcessingProperties props;
    private final TransactionTemplate tx;
    private final ObjectMapper json = new ObjectMapper();

    private volatile String lastError;
    private volatile Instant lastErrorAt;

    @Override
    public int processBatch(boolean useModel) {
        tx.executeWithoutResult(s -> stagingRepository.releaseStale(StagingStatus.PENDING,
                StagingStatus.PROCESSING, Instant.now().minus(props.getLeaseMinutes(), ChronoUnit.MINUTES)));

        // Closed tenders never need the model: move them in bulk so they do not queue
        // behind open ones. With the model on they are marked SKIPPED (never called), so
        // a later backfill knows they are done.
        int done = process(claim(true, props.getFastPathBatchSize()), useModel);
        done += process(claim(false, useModel ? props.getBatchSize() : props.getFastPathBatchSize()), useModel);
        return done;
    }

    @Override
    public int drainWithoutModel() {
        int total = 0;
        int n;
        while ((n = processBatch(false)) > 0) {
            total += n;
        }
        return total;
    }

    @Override
    public int scorePersisted() {
        List<TenderStaging> rows = stagingRepository.findByStatus(StagingStatus.PERSISTED);
        if (rows.isEmpty()) {
            return 0;
        }
        List<Long> ids = rows.stream().map(TenderStaging::getTenderId).filter(Objects::nonNull).distinct().toList();
        List<Tender> tenders = tenderRepository.findAllById(ids);
        List<Organisation> orgs = organisationRepository.findByActiveTrueOrderByIdAsc();
        int written = 0;
        for (Organisation org : orgs) {
            written += scoringService.scoreAll(org, tenders);
        }
        // Grades are comparative: new scores move every company's thresholds.
        orgs.forEach(scoringService::recalibrateGrades);
        rows.forEach(r -> r.setStatus(StagingStatus.DONE));
        stagingRepository.saveAll(rows);
        log.info("scored {} newly processed tenders for {} companies ({} rows)", tenders.size(), orgs.size(), written);
        return written;
    }

    @Override
    public ProcessingStatusResponse status() {
        LocalDateTime now = LocalDateTime.now();
        long waitingOpen = stagingRepository.countOpen(StagingStatus.PENDING, now);
        Double avgMs = stagingRepository.averageModelMillis();
        Long eta = avgMs == null ? null : Math.round(waitingOpen * avgMs / 60_000.0);
        return new ProcessingStatusResponse(
                llm.isEnabled(), props.isEnabled(), llmClient.model(),
                stagingRepository.countByStatus(StagingStatus.PENDING),
                waitingOpen,
                stagingRepository.countByStatus(StagingStatus.PROCESSING),
                stagingRepository.countByStatus(StagingStatus.PERSISTED),
                stagingRepository.countByStatus(StagingStatus.DONE),
                stagingRepository.countByStatus(StagingStatus.FAILED),
                stagingRepository.countModelReadsSince(Instant.now().minus(1, ChronoUnit.HOURS)),
                avgMs == null ? null : Math.round(avgMs / 100.0) / 10.0,
                eta, lastError, lastErrorAt);
    }

    // ---------------------------------------------------------------- internals

    private List<TenderStaging> claim(boolean closed, int limit) {
        return tx.execute(s -> {
            LocalDateTime now = LocalDateTime.now();
            List<TenderStaging> rows = closed
                    ? stagingRepository.lockClosed(now, limit)
                    : stagingRepository.lockOpen(now, limit);
            Instant at = Instant.now();
            rows.forEach(r -> {
                r.setStatus(StagingStatus.PROCESSING);
                r.setLockedAt(at);
            });
            return stagingRepository.saveAll(rows);
        });
    }

    private int process(List<TenderStaging> rows, boolean useModel) {
        int done = 0;
        for (int i = 0; i < rows.size(); i++) {
            TenderStaging row = rows.get(i);
            try {
                if (processOne(row, useModel)) {
                    done++;
                }
            } catch (LlmUnavailableException e) {
                // Not the tender's fault. Put this one and the rest back, untouched, and
                // stop: the next tick tries again.
                remember(e.getMessage());
                log.warn("model unavailable -- {} tenders stay queued: {}", rows.size() - i, e.getMessage());
                rows.subList(i, rows.size()).forEach(r -> requeue(r, e.getMessage(), false));
                break;
            } catch (Exception e) {
                log.error("could not process staged {} {}", row.getSourcePortal(), row.getExternalId(), e);
                row.setStatus(StagingStatus.FAILED);
                row.setLastError(truncate(e.getMessage()));
                row.setLockedAt(null);
                stagingRepository.save(row);
            }
        }
        return done;
    }

    /** @return true when the row reached the tender table (false: queued again). */
    private boolean processOne(TenderStaging row, boolean useModel) {
        Tender incoming = json.readValue(row.getParsedJson(), Tender.class);
        incoming.setId(null);
        ingestionService.prepare(incoming);

        Optional<Tender> existing = tenderRepository.findBySourcePortalAndExternalId(
                incoming.getSourcePortal(), incoming.getExternalId());
        String inputHash = TenderStagingServiceImpl.aiInputHash(incoming, llmClient.model());
        boolean opportunity = (incoming.getClosingAt() == null || !incoming.getClosingAt().isBefore(LocalDateTime.now()))
                && (incoming.getNoticeType() == null || incoming.getNoticeType().isOpportunity());

        boolean requirementsChanged = false;
        if (!useModel) {
            existing.ifPresent(e -> copyAi(e, incoming));
        } else if (!opportunity) {
            applyAi(incoming, TenderEnrichment.empty(), AiStatus.SKIPPED, inputHash);
        } else if (existing.isPresent() && existing.get().getAiStatus() == AiStatus.DONE
                && inputHash.equals(existing.get().getAiInputHash())) {
            copyAi(existing.get(), incoming);   // this exact reading is already stored
        } else {
            long started = System.currentTimeMillis();
            try {
                TenderEnrichment raw = llmClient.enrich(incoming, row.getAttempts());
                EnrichmentValidator.Result checked = EnrichmentValidator.validate(raw, incoming, llm.getLongTitleChars());
                if (!checked.dropped().isEmpty()) {
                    log.info("{} {}: dropped {}", incoming.getSourcePortal(), incoming.getExternalId(), checked.dropped());
                }
                applyAi(incoming, checked.kept(), AiStatus.DONE, inputHash);
                row.setAiUsed(true);
                row.setAiDurationMs(System.currentTimeMillis() - started);
                // The model just answered, so whatever went wrong before is over: the
                // Pipeline screen shows current problems, not ones already recovered from.
                lastError = null;
                lastErrorAt = null;
                requirementsChanged = existing.map(e -> !Objects.equals(e.getAiMinTurnoverBdt(),
                        incoming.getAiMinTurnoverBdt())).orElse(false);
            } catch (LlmCallException e) {
                row.setAttempts(row.getAttempts() + 1);
                if (row.getAttempts() < props.getMaxAttempts()) {
                    requeue(row, e.getMessage(), true);
                    return false;
                }
                // Twice unusable: go live with the portal's data rather than block the queue.
                log.warn("{} {}: model failed {} times, going live without it: {}", incoming.getSourcePortal(),
                        incoming.getExternalId(), row.getAttempts(), e.getMessage());
                applyAi(incoming, TenderEnrichment.empty(), AiStatus.FAILED, inputHash);
                row.setLastError(truncate(e.getMessage()));
            }
        }

        TenderIngestionService.PersistOutcome out = ingestionService.persist(incoming);
        row.setTenderId(out.tender().getId());
        // New or changed content -- or a new turnover requirement -- must be scored again.
        row.setStatus(out.contentChanged() || requirementsChanged ? StagingStatus.PERSISTED : StagingStatus.DONE);
        row.setProcessedAt(Instant.now());
        row.setLockedAt(null);
        stagingRepository.save(row);
        return true;
    }

    private void applyAi(Tender t, TenderEnrichment e, AiStatus status, String inputHash) {
        t.setAiShortTitle(e.shortTitle());
        t.setAiSummary(e.summary());
        t.setAiDeliverables(e.deliverables().isEmpty() ? null : e.deliverables().toArray(String[]::new));
        t.setAiLocation(e.location());
        t.setAiMinTurnoverBdt(e.minTurnoverBdt());
        t.setAiMinExperienceYears(e.minExperienceYears());
        t.setAiCertifications(e.certifications().isEmpty() ? null : e.certifications().toArray(String[]::new));
        t.setAiStatus(status);
        t.setAiModel(llmClient.model());
        t.setAiPromptVersion(EnrichmentPrompt.VERSION);
        t.setAiInputHash(inputHash);
        t.setAiProcessedAt(Instant.now());
    }

    private static void copyAi(Tender from, Tender to) {
        to.setAiShortTitle(from.getAiShortTitle());
        to.setAiSummary(from.getAiSummary());
        to.setAiDeliverables(from.getAiDeliverables());
        to.setAiLocation(from.getAiLocation());
        to.setAiMinTurnoverBdt(from.getAiMinTurnoverBdt());
        to.setAiMinExperienceYears(from.getAiMinExperienceYears());
        to.setAiCertifications(from.getAiCertifications());
        to.setAiStatus(from.getAiStatus());
        to.setAiModel(from.getAiModel());
        to.setAiPromptVersion(from.getAiPromptVersion());
        to.setAiInputHash(from.getAiInputHash());
        to.setAiProcessedAt(from.getAiProcessedAt());
    }

    private void requeue(TenderStaging row, String error, boolean countedAttempt) {
        row.setStatus(StagingStatus.PENDING);
        row.setLockedAt(null);
        row.setLastError(truncate(error));
        stagingRepository.save(row);
        if (countedAttempt) {
            remember(error);
        }
    }

    private void remember(String error) {
        lastError = truncate(error);
        lastErrorAt = Instant.now();
    }

    private static String truncate(String s) {
        return s == null ? null : (s.length() <= 1000 ? s : s.substring(0, 1000));
    }
}
