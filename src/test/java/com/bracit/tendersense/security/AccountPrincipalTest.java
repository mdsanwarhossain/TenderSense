package com.bracit.tendersense.security;

import com.bracit.tendersense.entity.Account;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountPrincipalTest {

    private static Organisation company(boolean active) {
        Organisation org = mock(Organisation.class);
        when(org.getId()).thenReturn(7L);
        when(org.isActive()).thenReturn(active);
        return org;
    }

    private static Account account(Organisation org, Role role, Boolean enabled) {
        return Account.builder().id(1L).email("a@b.com").passwordHash("hash")
                .organisation(org).role(role).enabled(enabled).build();
    }

    private static Set<String> roles(AccountPrincipal p) {
        return p.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("a company account is USER; a row from before roles (null) reads the same")
    void companyAccountIsUser() {
        assertEquals(Set.of("ROLE_USER"), roles(AccountPrincipal.forSession(account(company(true), Role.USER, true))));
        AccountPrincipal legacy = AccountPrincipal.forSession(account(company(true), null, null));
        assertEquals(Set.of("ROLE_USER"), roles(legacy));
        assertTrue(legacy.isEnabled(), "null enabled means switched on");
    }

    @Test
    @DisplayName("a platform admin has no company, so no company screens")
    void platformAdminIsOnlyAdmin() {
        AccountPrincipal p = AccountPrincipal.forSession(account(null, Role.ADMIN, true));
        assertEquals(Set.of("ROLE_ADMIN"), roles(p));
        assertNull(p.getOrganisationId());
    }

    @Test
    @DisplayName("a company account promoted to admin keeps its company screens")
    void promotedCompanyAccountIsBoth() {
        assertEquals(Set.of("ROLE_USER", "ROLE_ADMIN"),
                roles(AccountPrincipal.forSession(account(company(true), Role.ADMIN, true))));
    }

    @Test
    @DisplayName("switched off, or in a switched-off company, cannot sign in")
    void disabled() {
        assertFalse(AccountPrincipal.forSession(account(company(true), Role.USER, false)).isEnabled());
        assertFalse(AccountPrincipal.forSession(account(company(false), Role.USER, true)).isEnabled());
    }

    @Test
    @DisplayName("the session copy never carries the password hash")
    void sessionCopyHasNoHash() {
        Account a = account(company(true), Role.USER, true);
        assertNull(AccountPrincipal.forSession(a).getPassword());
        AccountPrincipal signIn = AccountPrincipal.forSignIn(a);
        assertEquals("hash", signIn.getPassword());
        signIn.eraseCredentials();
        assertNull(signIn.getPassword());
    }
}
