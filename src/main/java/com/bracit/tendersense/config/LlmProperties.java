package com.bracit.tendersense.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The local model that reads tenders between the staging table and the tender table.
 *
 * <p>Off by default, so tests and any instance without Ollama keep the old behaviour:
 * fetched tenders are standardised by rules and go straight into the tender table.
 */
@Getter @Setter
@ConfigurationProperties(prefix = "tendersense.llm")
public class LlmProperties {

    private boolean enabled = false;

    private String baseUrl = "http://localhost:11434";

    private String model = "qwen2.5:7b";

    /**
     * Context window. Ollama's default is small, and an overflowing prompt is cut from
     * the front -- which would silently drop the instructions.
     */
    private int numCtx = 8192;

    /** Leaves cores for the application and the embedding model on a shared CPU. */
    private int numThread = 16;

    private int seed = 7;

    /** A long notice on CPU takes ~90 s; this is the ceiling for one call. */
    private int readTimeoutSeconds = 300;

    /** Keeps the model loaded between batches instead of reloading it (~15 s) each time. */
    private String keepAlive = "30m";

    /** Titles longer than this get an AI short title. */
    private int longTitleChars = 150;

    /**
     * The model that writes the tender-against-profile comparison on the detail page.
     *
     * <p>A smaller model than the batch one on purpose: this is the only model call a
     * person ever waits for, and a 3B answers a short comparison in a few seconds where
     * the 7B takes ~12s of generation on this CPU. Reading a tender properly (the batch
     * job, which nobody waits for) stays with the larger model.
     */
    private String summaryModel = "qwen2.5:3b";

    /** Leaves cores for the batch model, which may be mid-tender when a reader arrives. */
    private int summaryNumThread = 8;

    /** A reader is watching a spinner: give up and fall back well before the batch ceiling. */
    private int summaryReadTimeoutSeconds = 120;

    /**
     * Room for the comparison to finish. At 320 the model stopped mid-word on a tender
     * with a long profile, and an unfinished sentence is dropped rather than shown -- so
     * the ceiling is set above what a complete answer needs.
     */
    private int summaryNumPredict = 460;

    /** How long a written comparison stays good. A day, as agreed. */
    private int summaryCacheHours = 24;
}
