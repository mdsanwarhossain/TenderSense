package com.bracit.tendersense.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Second-stage scoring by a local LLM. See application.properties for the reasoning. */
@Getter
@Setter
@ConfigurationProperties(prefix = "tendersense.llm")
public class LlmProperties {

    /** Kill switch. False stops new reviews; stored verdicts stay for comparison. */
    private boolean enabled = false;
    private String model = "qwen2.5:7b";
    /** How many of a company's top open tenders, by embedding score, get a verdict. */
    private int topN = 25;
    private int numCtx = 8192;
    /** Attempts per tender for a malformed or out-of-range reply. */
    private int maxAttempts = 2;
    private int seed = 42;
}
