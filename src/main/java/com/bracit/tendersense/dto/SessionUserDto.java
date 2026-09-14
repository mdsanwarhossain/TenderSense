package com.bracit.tendersense.dto;

import com.bracit.tendersense.entity.Account;
import com.bracit.tendersense.entity.enums.Role;

/**
 * Who is signed in: returned by login, signup and /api/auth/me.
 *
 * @param organisation the company, or null for a platform admin
 */
public record SessionUserDto(Long accountId, String email, Role role, OrganisationDto organisation) {

    public static SessionUserDto of(Account account) {
        return new SessionUserDto(account.getId(), account.getEmail(), account.effectiveRole(),
                account.getOrganisation() == null ? null : OrganisationDto.of(account.getOrganisation()));
    }
}
