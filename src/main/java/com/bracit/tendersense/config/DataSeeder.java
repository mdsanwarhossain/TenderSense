package com.bracit.tendersense.config;

import com.bracit.tendersense.entity.PipelineRun;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.entity.enums.RunStatus;
import com.bracit.tendersense.repository.PipelineRunRepository;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.util.SectorClassifier;
import com.bracit.tendersense.service.CapabilityProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/** Ensures a capability profile exists so scoring has something to match against. */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final CapabilityProfileService profileService;
    private final PipelineRunRepository runRepository;
    private final TenderRepository tenderRepository;
    private final SectorClassifier sectorClassifier;

    @Override
    public void run(ApplicationArguments args) {
        profileService.seedIfEmpty();
        closeOrphanedRuns();
        backfillSectors();
    }

    /**
     * Classifies tenders stored before the classifier existed.
     *
     * <p>Ingestion only re-reads a tender when its content hash or parser version
     * changes, which is correct — but it means World Bank rows, whose payload has not
     * changed, would never pick up a sector. This runs once and is idempotent.
     */
    private void backfillSectors() {
        List<Tender> unclassified = tenderRepository.findBySectorIsNull();
        if (unclassified.isEmpty()) {
            return;
        }
        for (Tender t : unclassified) {
            t.setCpvTop(sectorClassifier.topLevel(t.getCpvRaw()));
            t.setSector(t.getSourcePortal() == SourcePortal.WORLD_BANK
                    ? sectorClassifier.classifyWorldBank(
                            t.getProcurementNature(), t.getProcurementType(), t.getTitle())
                    : sectorClassifier.classify(
                            t.getCpvRaw(), t.getProcurementMethod(), t.getTitle()));
        }
        tenderRepository.saveAll(unclassified);
        log.info("backfilled sector on {} tenders", unclassified.size());
    }

    /**
     * A run that was in flight when the process died stays RUNNING for ever, and the
     * pipeline screen shows it spinning indefinitely. Nothing can still be executing
     * at startup, so any such row is closed out honestly as interrupted.
     */
    private void closeOrphanedRuns() {
        java.util.List<PipelineRun> orphaned = runRepository.findByStatus(RunStatus.RUNNING);
        if (orphaned.isEmpty()) {
            return;
        }
        java.time.Instant now = java.time.Instant.now();
        for (PipelineRun run : orphaned) {
            run.setStatus(RunStatus.FAILED);
            run.setFinishedAt(now);
            run.setErrorMessage("interrupted - the application restarted while this run was active");
            if (run.getStartedAt() != null) {
                run.setDurationMs(java.time.Duration.between(run.getStartedAt(), now).toMillis());
            }
        }
        runRepository.saveAll(orphaned);
        log.warn("closed {} pipeline run(s) left RUNNING by a previous shutdown", orphaned.size());
    }
}
