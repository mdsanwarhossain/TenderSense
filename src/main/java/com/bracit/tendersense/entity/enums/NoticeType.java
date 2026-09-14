package com.bracit.tendersense.entity.enums;

/** What kind of notice this is. Only TENDER, EXPRESSION_OF_INTEREST and PREQUALIFICATION are bidding opportunities. */
public enum NoticeType {
    TENDER,
    EXPRESSION_OF_INTEREST,
    PREQUALIFICATION,
    /** Announces who won -- not something to bid on. */
    CONTRACT_AWARD,
    GENERAL_NOTICE;

    public boolean isOpportunity() {
        return this == TENDER || this == EXPRESSION_OF_INTEREST || this == PREQUALIFICATION;
    }
}
