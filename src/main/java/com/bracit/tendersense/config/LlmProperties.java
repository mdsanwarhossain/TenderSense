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
}
