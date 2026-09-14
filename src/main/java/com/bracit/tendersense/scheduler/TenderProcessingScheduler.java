package com.bracit.tendersense.scheduler;

import com.bracit.tendersense.config.LlmProperties;
import com.bracit.tendersense.config.ProcessingProperties;
import com.bracit.tendersense.service.PipelineLock;
import com.bracit.tendersense.service.TenderProcessingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Works through the staging table: a batch, then a pause, then the next batch.
 *
 * <p>Fixed delay, not a fixed rate: a batch of ten tenders through the model takes
 * minutes on CPU, and the next one must not start until it has finished. Separate from
 * {@code tendersense.schedule.enabled}, so an instance that does not collect -- the demo
 * profile -- can still work through the backlog.
 */
@Component
@ConditionalOnProperty(name = "tendersense.processing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class TenderProcessingScheduler {

    private final TenderProcessingService processing;
    private final PipelineLock pipelineLock;
    private final LlmProperties llm;
    private final ProcessingProperties props;

    @PostConstruct
    void announce() {
        log.info("tender processing ARMED: {} per batch every {} ms, model {}", props.getBatchSize(),
                props.getDelayMs(), llm.isEnabled() ? llm.getModel() + " at " + llm.getBaseUrl() : "OFF (rules only)");
    }

    @Scheduled(fixedDelayString = "${tendersense.processing.delay-ms:30000}", initialDelay = 20_000)
    public void tick() {
        try {
            int processed = processing.processBatch(llm.isEnabled());
            // Scoring writes match rows: never alongside a rescore or a collection run.
            // If one is running, the rows wait (PERSISTED) for the next tick.
            pipelineLock.runExclusively("processing:score", processing::scorePersisted);
            if (processed > 0) {
                log.info("processing tick: {} tenders moved to the tender table", processed);
            }
        } catch (Exception e) {
            log.error("processing tick failed", e);
        }
    }
}
