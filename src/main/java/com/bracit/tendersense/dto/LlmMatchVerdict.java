package com.bracit.tendersense.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * What the local LLM returns for one tender. {@code BeanOutputConverter} derives a JSON
 * schema from this record, and that schema is handed to Ollama as its {@code format} so
 * decoding is constrained to it -- the annotations below are the schema, not decoration.
 */
public record LlmMatchVerdict(

        @JsonProperty(value = "match_score", required = true)
        @JsonPropertyDescription("How well the tender fits the company, 0 to 100, using the rubric bands.")
        int matchScore,

        @JsonProperty(value = "reasoning", required = true)
        @JsonPropertyDescription("At most two sentences naming the specific capability or requirement that decided the score.")
        String reasoning) {
}
