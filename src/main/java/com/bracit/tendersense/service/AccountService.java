package com.bracit.tendersense.service;

import com.bracit.tendersense.entity.Account;
import com.bracit.tendersense.entity.Organisation;

public interface AccountService {

    /**
     * Verifies credentials and stamps the login time.
     *
     * @throws com.bracit.tendersense.exception.UnauthenticatedException with an identical
     *         message whether the email is unknown or the password is wrong -- a caller
     *         must not be able to use this endpoint to discover which companies exist.
     */
    Account authenticate(String email, String rawPassword);

    /** Creates the single account for a company. Fails if the email is already taken. */
    Account createFor(Organisation organisation, String email, String rawPassword);

    /** Gives a seeded company an account if it has none. Used at startup, never at runtime. */
    void seedFor(Organisation organisation, String email);

    boolean emailTaken(String email);
}
