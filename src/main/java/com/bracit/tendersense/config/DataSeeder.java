package com.bracit.tendersense.config;

import com.bracit.tendersense.entity.PipelineRun;
import com.bracit.tendersense.entity.enums.RunStatus;
import com.bracit.tendersense.repository.PipelineRunRepository;
import com.bracit.tendersense.service.CapabilityProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Ensures a capability profile exists so scoring has something to match against. */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final CapabilityProfileService profileService;
    private final PipelineRunRepository runRepository;

    @Override
    public void run(ApplicationArguments args) {
        profileService.seedIfEmpty();
        closeOrphanedRuns();
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
