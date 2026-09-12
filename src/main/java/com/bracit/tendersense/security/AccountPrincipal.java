package com.bracit.tendersense.security;

import com.bracit.tendersense.entity.Account;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.Role;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The signed-in account, as Spring Security holds it in the session.
 *
 * <p>Authorities follow what the account can reach, not just its role: ROLE_USER only
 * when it belongs to a company (the company screens need one), ROLE_ADMIN when its role
 * is ADMIN. A platform admin therefore gets only ROLE_ADMIN; a company account promoted
 * to admin gets both.
 */
public final class AccountPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long accountId;
    private final String email;
    private String passwordHash;
    private final Long organisationId;
    private final Role role;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    private AccountPrincipal(Account account, String passwordHash) {
        Organisation org = account.getOrganisation();
        this.accountId = account.getId();
        this.email = account.getEmail();
        this.passwordHash = passwordHash;
        this.organisationId = org == null ? null : org.getId();
        this.role = account.effectiveRole();
        this.enabled = account.isSwitchedOn() && (org == null || org.isActive());
        this.authorities = authoritiesFor(role, organisationId != null);
    }

    /** With the password hash, for checking a sign-in. */
    public static AccountPrincipal forSignIn(Account account) {
        return new AccountPrincipal(account, account.getPasswordHash());
    }

    /** Without it, for what is kept in the session afterwards. */
    public static AccountPrincipal forSession(Account account) {
        return new AccountPrincipal(account, null);
    }

    static List<GrantedAuthority> authoritiesFor(Role role, boolean hasCompany) {
        List<GrantedAuthority> out = new ArrayList<>(2);
        if (hasCompany) {
            out.add(new SimpleGrantedAuthority("ROLE_USER"));
        }
        if (role == Role.ADMIN) {
            out.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return List.copyOf(out);
    }

    public Long getAccountId() {
        return accountId;
    }

    /** Null for a platform admin. */
    public Long getOrganisationId() {
        return organisationId;
    }

    public Role getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** False when the account is switched off or its company is deactivated. */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AccountPrincipal other && Objects.equals(accountId, other.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(accountId);
    }

    @Override
    public String toString() {
        return "AccountPrincipal[" + email + ", " + role + ", org=" + organisationId + "]";
    }
}
