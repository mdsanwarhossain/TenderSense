package com.bracit.tendersense.dto;

import java.util.List;

/**
 * The model's reading of one tender against one company's profile -- not yet validated.
 *
 * @param comparison two sentences: what the tender needs, and how far this company's work covers it
 * @param matches    lines naming both sides, "what the tender asks — the work that covers it"
 * @param gaps       what the tender needs that the profile does not show; empty when it covers it
 */
public record MatchComparison(String comparison, List<String> matches, List<String> gaps) {
}
