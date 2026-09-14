package com.bracit.tendersense.entity.enums;

/** Outcome of the local model's reading of one tender. */
public enum AiStatus {
    /** Read; the fields that passed validation are stored. */
    DONE,
    /** The model answered badly twice; the tender went live with the portal's data only. */
    FAILED,
    /** Not worth the model's time: closed, or not a bidding opportunity. */
    SKIPPED
}
