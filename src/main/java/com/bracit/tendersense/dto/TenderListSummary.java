package com.bracit.tendersense.dto;

/**
 * Counts for the tender list's summary cards. Each (but {@code total}) is also a filter
 * on the table, and each count equals what that filter returns.
 *
 * @param total       tenders in scope, across every grade
 * @param sGrade      of those, S-grade
 * @param closingSoon of those, closing today or within the next seven days
 * @param saved       of those, saved by this company
 * @param submitted   marked submitted by this company -- closed tenders included
 */
public record TenderListSummary(long total, long sGrade, long closingSoon, long saved, long submitted) {
}
