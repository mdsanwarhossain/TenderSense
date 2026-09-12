package com.bracit.tendersense.security;

import com.bracit.tendersense.entity.Account;
import com.bracit.tendersense.repository.AccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Makes an admin's change take effect on the user's very next request, not at their next
 * sign-in.
 *
 * <p>The session holds the account as it was when it signed in. This re-reads it (one
 * primary-key lookup): an account switched off, or a company deactivated, is signed out
 * with a 401; a changed role is swapped into the session so the new access applies now.
 */
public class AccountStatusFilter extends OncePerRequestFilter {

    private final AccountRepository accountRepository;
    private final SecurityContextRepository contextRepository;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    public AccountStatusFilter(AccountRepository accountRepository, SecurityContextRepository contextRepository) {
        this.accountRepository = accountRepository;
        this.contextRepository = contextRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = holder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AccountPrincipal signedIn) {
            Account current = accountRepository.findById(signedIn.getAccountId()).orElse(null);
            AccountPrincipal fresh = current == null ? null : AccountPrincipal.forSession(current);

            if (fresh == null || !fresh.isEnabled()) {
                holder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                ProblemResponses.write(response, HttpStatus.UNAUTHORIZED,
                        "This account or its company has been switched off. Contact TenderSense.");
                return;
            }

            if (!names(fresh.getAuthorities()).equals(names(auth.getAuthorities()))) {
                SecurityContext updated = holder.createEmptyContext();
                updated.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        fresh, null, fresh.getAuthorities()));
                holder.setContext(updated);
                contextRepository.saveContext(updated, request, response);
            }
        }
        chain.doFilter(request, response);
    }

    private static Set<String> names(java.util.Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}
