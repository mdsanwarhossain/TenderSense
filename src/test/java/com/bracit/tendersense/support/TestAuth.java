package com.bracit.tendersense.support;

import com.bracit.tendersense.entity.Account;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.Role;
import com.bracit.tendersense.repository.AccountRepository;
import com.bracit.tendersense.security.AccountPrincipal;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Signs a MockMvc request in as a real account from the database, with a valid CSRF
 * token -- replacing the old X-Org-Id header bypass, which is gone from the application.
 */
public final class TestAuth {

    private TestAuth() {
    }

    public static RequestPostProcessor as(Account account) {
        RequestPostProcessor signedIn = user(AccountPrincipal.forSession(account));
        RequestPostProcessor token = csrf();
        return request -> token.postProcessRequest(signedIn.postProcessRequest(request));
    }

    /** The company's own account. */
    public static RequestPostProcessor company(AccountRepository accounts, Organisation organisation) {
        return as(accounts.findByOrganisationId(organisation.getId())
                .orElseThrow(() -> new IllegalStateException("no account for " + organisation.getSlug())));
    }

    /** The first platform admin (seeded at startup). */
    public static RequestPostProcessor admin(AccountRepository accounts) {
        return as(accounts.findFirstByRoleOrderByIdAsc(Role.ADMIN)
                .orElseThrow(() -> new IllegalStateException("no admin account seeded")));
    }
}
