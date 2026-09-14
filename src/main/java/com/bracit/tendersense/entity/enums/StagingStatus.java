package com.bracit.tendersense.entity.enums;

/** Where a fetched tender is on its way from the staging table to the tender list. */
public enum StagingStatus {
    /** Waiting for the worker. */
    PENDING,
    /** Claimed by a batch (see lockedAt). */
    PROCESSING,
    /** Written to the tender table, not yet scored for every company. */
    PERSISTED,
    DONE,
    /** Could not be processed at all (unreadable payload); needs a developer. */
    FAILED
}
