package com.bracit.tendersense.service.admin.impl;

import com.bracit.tendersense.dto.admin.AdminRequests;
import com.bracit.tendersense.dto.admin.AdminUserResponse;
import com.bracit.tendersense.entity.Account;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.Role;
import com.bracit.tendersense.exception.NotFoundException;
import com.bracit.tendersense.repository.AccountRepository;
import com.bracit.tendersense.service.AccountService;
import com.bracit.tendersense.service.admin.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdminUserServiceImpl implements AdminUserService {

    static final int MIN_PASSWORD = 8;

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<AdminUserResponse> list(Long actorAccountId) {
        return accountRepository.findAll().stream()
                // Staff first, then companies A-Z.
                .sorted(Comparator.comparing((Account a) -> a.effectiveRole() != Role.ADMIN)
                        .thenComparing(a -> a.getOrganisation() == null ? "" : a.getOrganisation().getName(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Account::getEmail))
                .map(a -> toResponse(a, actorAccountId))
                .toList();
    }

    @Override
    @Transactional
    public AdminUserResponse update(Long actorAccountId, Long accountId, AdminRequests.UserUpdate request) {
        Account account = require(accountId);
        Role role = request.role() == null ? account.effectiveRole() : request.role();
        boolean enabled = request.enabled() == null ? account.isSwitchedOn() : request.enabled();

        boolean wasActiveAdmin = isActiveAdmin(account);
        boolean staysActiveAdmin = role == Role.ADMIN && enabled;

        if (Objects.equals(actorAccountId, accountId) && wasActiveAdmin && !staysActiveAdmin) {
            throw new IllegalArgumentException(
                    "You can't remove your own admin access or switch off your own account. Ask another admin.");
        }
        if (role == Role.USER && account.getOrganisation() == null) {
            throw new IllegalArgumentException(
                    "This account has no company, so it can only be an admin.");
        }
        if (wasActiveAdmin && !staysActiveAdmin && otherActiveAdmins(accountId) == 0) {
            throw new IllegalArgumentException("TenderSense needs at least one active admin.");
        }

        account.setRole(role);
        account.setEnabled(enabled);
        accountRepository.save(account);
        log.info("account {} set to {} / {} by account {}", account.getEmail(), role,
                enabled ? "on" : "off", actorAccountId);
        return toResponse(account, actorAccountId);
    }

    @Override
    @Transactional
    public void resetPassword(Long accountId, String rawPassword) {
        Account account = require(accountId);
        requireStrong(rawPassword);
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        accountRepository.save(account);
        log.info("password reset for {}", account.getEmail());
    }

    @Override
    @Transactional
    public AdminUserResponse createAdmin(Long actorAccountId, AdminRequests.NewAdmin request) {
        String email = AccountService.normaliseEmail(request.email());
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        if (accountRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("That email already has an account.");
        }
        requireStrong(request.password());
        Account admin = accountRepository.save(Account.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.ADMIN)
                .enabled(true)
                .createdAt(Instant.now())
                .build());
        log.info("platform admin {} created by account {}", email, actorAccountId);
        return toResponse(admin, actorAccountId);
    }

    private long otherActiveAdmins(Long excludingAccountId) {
        return accountRepository.findByRole(Role.ADMIN).stream()
                .filter(a -> !a.getId().equals(excludingAccountId))
                .filter(AdminUserServiceImpl::isActiveAdmin)
                .count();
    }

    /** An admin who can actually sign in: switched on, and not in a switched-off company. */
    private static boolean isActiveAdmin(Account a) {
        return a.effectiveRole() == Role.ADMIN && a.isSwitchedOn()
                && (a.getOrganisation() == null || a.getOrganisation().isActive());
    }

    private static void requireStrong(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD) {
            throw new IllegalArgumentException("Passwords need at least " + MIN_PASSWORD + " characters.");
        }
    }

    private Account require(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("account " + id + " not found"));
    }

    private static AdminUserResponse toResponse(Account a, Long actorAccountId) {
        Organisation org = a.getOrganisation();
        return new AdminUserResponse(a.getId(), a.getEmail(), a.effectiveRole(), a.isSwitchedOn(),
                org == null ? null : org.getId(),
                org == null ? null : org.getName(),
                org == null || org.isActive(),
                a.getCreatedAt(), a.getLastLoginAt(),
                Objects.equals(a.getId(), actorAccountId));
    }
}
