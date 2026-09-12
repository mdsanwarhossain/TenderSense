package com.bracit.tendersense.entity.enums;

/**
 * How the tender list is ordered.
 *
 * <p>{@code BEST_MATCH} is the list's reason for existing and stays the default: the
 * whole point is that the strongest fit is at the top. {@code NEWEST} answers a different
 * question -- "what has just arrived" -- which is what the dashboard's New matches card
 * links to, and what a bid team wants after a collection run.
 */
public enum ShortlistSort {

    BEST_MATCH,

    /** Most recently published first. Tenders with no published date sort last. */
    NEWEST
}
