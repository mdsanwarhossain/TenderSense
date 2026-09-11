package com.bracit.tendersense.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** The worker that moves tenders from the staging table into the tender table. */
@Getter @Setter
@ConfigurationProperties(prefix = "tendersense.processing")
public class ProcessingProperties {

    /** Runs the worker on its own schedule. Off in tests; the `ai` profile turns it on. */
    private boolean enabled = false;

    /** Open tenders per model batch: the model reads them one after another. */
    private int batchSize = 10;

    /** Closed tenders need no model, so they move in bigger batches. */
    private int fastPathBatchSize = 200;

    private long delayMs = 30_000;

    /** A row claimed longer ago than this was abandoned by a crash and is claimed again. */
    private int leaseMinutes = 15;

    /** Bad model answers before the tender goes live with the portal's data only. */
    private int maxAttempts = 2;
}
