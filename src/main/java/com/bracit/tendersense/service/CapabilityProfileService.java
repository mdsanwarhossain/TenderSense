package com.bracit.tendersense.service;

import com.bracit.tendersense.entity.CapabilityProfile;

import java.util.List;

public interface CapabilityProfileService {

    CapabilityProfile current();

    /**
     * The profile flattened into individually embeddable statements. Matching
     * compares a tender against each of these rather than one blob, so evidence
     * can name which capability matched.
     */
    List<String> capabilityStatements();

    /** The "not our work" statements matching scores against as a penalty. */
    List<String> exclusionStatements();

    /** Seeds the profile from resources when the database has none. */
    void seedIfEmpty();
}
