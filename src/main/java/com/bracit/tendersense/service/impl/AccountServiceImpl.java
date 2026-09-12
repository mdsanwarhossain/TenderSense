package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.entity.Account;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.Role;
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

import static com.bracit.tendersense.service.AccountService.normaliseEmail;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

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
    public Account recordLogin(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new UnauthenticatedException("Not signed in"));
        account.setLastLoginAt(Instant.now());
        return accountRepository.save(account);
    }

    @Override
    @Transactional
    public Account createFor(Organisation organisation, String email, String rawPassword) {
        String normalised = normaliseEmail(email);
        if (accountRepository.existsByEmail(normalised)) {
            throw new IllegalArgumentException("That email already has an account");
        }
        return accountRepository.save(Account.builder()
                .organisation(organisation)
                .email(normalised)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(Role.USER)
                .enabled(true)
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
                normaliseEmail(email), organisation.getSlug());
    }

    @Override
    @Transactional
    public void seedAdmin(String email, String rawPassword) {
        if (accountRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        String normalised = normaliseEmail(email);
        if (accountRepository.existsByEmail(normalised)) {
            log.warn("no platform admin seeded: {} already belongs to a company account", normalised);
            return;
        }
        accountRepository.save(Account.builder()
                .email(normalised)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(Role.ADMIN)
                .enabled(true)
                .createdAt(Instant.now())
                .build());
        log.info("seeded platform admin {} -- password from tendersense.auth.admin-password", normalised);
    }

    @Override
    public boolean emailTaken(String email) {
        return email != null && accountRepository.existsByEmail(normaliseEmail(email));
    }
}
