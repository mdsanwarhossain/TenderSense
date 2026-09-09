package com.bracit.tendersense.entity.enums;

public enum EligibilityStatus {
    ELIGIBLE,
    INELIGIBLE,
    /** A hard requirement was found but could not be evaluated from portal data. */
    NEEDS_VERIFICATION
}
