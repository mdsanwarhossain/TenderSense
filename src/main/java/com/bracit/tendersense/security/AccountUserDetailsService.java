package com.bracit.tendersense.security;

import com.bracit.tendersense.repository.AccountRepository;
import com.bracit.tendersense.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Looks an account up by email for Spring Security's sign-in.
 *
 * <p>The password check itself is Spring's DaoAuthenticationProvider, which also hashes a
 * dummy password for an unknown email -- so a missing account and a wrong password take
 * the same time, as the hand-written check before it did.
 */
@Service
@RequiredArgsConstructor
public class AccountUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return accountRepository.findByEmail(AccountService.normaliseEmail(email))
                .map(AccountPrincipal::forSignIn)
                .orElseThrow(() -> new UsernameNotFoundException("no account"));
    }
}
