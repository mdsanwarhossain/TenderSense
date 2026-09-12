package com.bracit.tendersense.service;

import com.bracit.tendersense.entity.Account;
import com.bracit.tendersense.entity.Organisation;

import java.util.Locale;

/** Accounts: creating and seeding them. Checking a password is Spring Security's job. */
public interface AccountService {

    /** Stamps the sign-in time and returns the account as it now is. */
    Account recordLogin(Long accountId);

    /** Creates the single USER account for a company. Fails if the email is already taken. */
    Account createFor(Organisation organisation, String email, String rawPassword);

    /** Gives a seeded company an account if it has none. Used at startup, never at runtime. */
    void seedFor(Organisation organisation, String email);

    /** Creates the first platform admin if there is none. Used at startup. */
    void seedAdmin(String email, String rawPassword);

    boolean emailTaken(String email);

    /** Emails are stored lower-cased and trimmed, so sign-in is not case-sensitive. */
    static String normaliseEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
