package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.entity.Account;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.exception.UnauthenticatedException;
import com.bracit.tendersense.repository.AccountRepository;
import com.bracit.tendersense.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

    /** The same message for both failure modes. See {@link AccountService#authenticate}. */
    private static final String REJECTED = "Email or password is incorrect";

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Password given to the two seeded demo companies. Only ever applied to companies
     * created from the bundled profile JSON -- a company created through sign-up sets its
     * own. Printed at startup because a demo nobody can log into is worse than a known one.
     */
    @Value("${tendersense.auth.demo-password:tendersense}")
    private String demoPassword;

    @Override
    @Transactional
    public Account authenticate(String email, String rawPassword) {
        if (email == null || rawPassword == null) {
            throw new UnauthenticatedException(REJECTED);
        }
        Optional<Account> found = accountRepository.findByEmail(normalise(email));

        // Hash the supplied password even when the account is unknown, so that a missing
        // account and a wrong password take a comparable amount of time. Skipping the work
        // on the unknown-email branch turns response time into an account-existence oracle.
        String hash = found.map(Account::getPasswordHash).orElse(null);
        boolean ok = hash != null && passwordEncoder.matches(rawPassword, hash);
        if (!ok) {
            if (hash == null) {
                passwordEncoder.encode(rawPassword);
            }
            throw new UnauthenticatedException(REJECTED);
        }

        Account account = found.orElseThrow();
        if (!account.getOrganisation().isActive()) {
            throw new UnauthenticatedException(REJECTED);
        }
        account.setLastLoginAt(Instant.now());
        return accountRepository.save(account);
    }

    @Override
    @Transactional
    public Account createFor(Organisation organisation, String email, String rawPassword) {
        String normalised = normalise(email);
        if (accountRepository.existsByEmail(normalised)) {
            throw new IllegalArgumentException("That email already has an account");
        }
        return accountRepository.save(Account.builder()
                .organisation(organisation)
                .email(normalised)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .createdAt(Instant.now())
                .build());
    }

    @Override
    @Transactional
    public void seedFor(Organisation organisation, String email) {
        if (accountRepository.findByOrganisationId(organisation.getId()).isPresent()) {
            return;
        }
        createFor(organisation, email, demoPassword);
        log.info("seeded account {} for {} -- password from tendersense.auth.demo-password",
                normalise(email), organisation.getSlug());
    }

    @Override
    public boolean emailTaken(String email) {
        return email != null && accountRepository.existsByEmail(normalise(email));
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
