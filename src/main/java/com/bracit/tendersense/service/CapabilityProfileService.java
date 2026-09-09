package com.bracit.tendersense.service;

import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.Organisation;

import java.util.List;

public interface CapabilityProfileService {

    CapabilityProfile forOrganisation(Organisation organisation);

    /**
     * The profile flattened into individually embeddable statements. Matching
     * compares a tender against each of these rather than one blob, so evidence
     * can name which capability matched.
     */
    List<String> capabilityStatements(Organisation organisation);

    /** The "not our work" statements matching scores against as a penalty. */
    List<String> exclusionStatements(Organisation organisation);

    /** Seeds the profile from resources when the database has none. */
    /**
     * Replaces the editable parts of a company's profile and stamps {@code updatedAt}.
     * Sectors live on the {@link Organisation} and are updated alongside, because the
     * editor presents them as one screen.
     *
     * <p>Does not re-score: that is a separate, explicit action. See
     * {@code POST /api/pipeline/rescore}.
     */
    CapabilityProfile update(Organisation organisation,
                             com.bracit.tendersense.dto.ProfileUpdateRequest request);

    void seedMissing();
}
