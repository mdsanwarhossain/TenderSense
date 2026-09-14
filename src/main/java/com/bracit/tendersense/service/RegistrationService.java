package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.SignupRequest;
import com.bracit.tendersense.entity.Organisation;

public interface RegistrationService {

    /**
     * Registers a new company: an {@link Organisation}, its single account, and an empty
     * capability profile for it to fill in.
     *
     * @throws IllegalArgumentException on any invalid or already-taken field, with a
     *         message written for the person filling in the form
     */
    Organisation register(SignupRequest request);
}
