package com.bracit.tendersense.controller;

import com.bracit.tendersense.dto.LoginRequest;
import com.bracit.tendersense.dto.SessionUserDto;
import com.bracit.tendersense.dto.SignupRequest;
import com.bracit.tendersense.entity.Account;
import com.bracit.tendersense.exception.UnauthenticatedException;
import com.bracit.tendersense.repository.AccountRepository;
import com.bracit.tendersense.security.AccountPrincipal;
import com.bracit.tendersense.service.AccountService;
import com.bracit.tendersense.service.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

/**
 * Sign in, sign up, and "who am I". Sign-out is Spring Security's logout filter
 * ({@code POST /api/auth/logout}, see SecurityConfig).
 *
 * <p>The SPA posts JSON, so sign-in is a controller rather than a form login: it hands the
 * credentials to Spring's AuthenticationManager, then does what a form login would --
 * a new session id, a fresh CSRF token, the security context saved in the session.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    /** The same message whether the email is unknown or the password wrong. */
    static final String REJECTED = "Email or password is incorrect";

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final RegistrationService registrationService;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    /** Registers a new company and signs it straight in. */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionUserDto signup(@RequestBody SignupRequest request,
                                 HttpServletRequest http, HttpServletResponse response) {
        registrationService.register(request);
        return signIn(request.email(), request.password(), http, response);
    }

    @PostMapping("/login")
    public SessionUserDto login(@RequestBody LoginRequest request,
                                HttpServletRequest http, HttpServletResponse response) {
        return signIn(request.email(), request.password(), http, response);
    }

    /** The signed-in account. Signed out, the filter chain answers 401 before this runs. */
    @GetMapping("/me")
    public SessionUserDto me(@AuthenticationPrincipal AccountPrincipal principal) {
        if (principal == null) {
            throw new UnauthenticatedException("Not signed in");
        }
        Account account = accountRepository.findById(principal.getAccountId())
                .orElseThrow(() -> new UnauthenticatedException("Not signed in"));
        return SessionUserDto.of(account);
    }

    private SessionUserDto signIn(String email, String password,
                                  HttpServletRequest http, HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                    email == null ? "" : email.trim(), password == null ? "" : password));
        } catch (AuthenticationException e) {
            // Unknown email, wrong password, switched-off account or company: one answer.
            throw new UnauthenticatedException(REJECTED);
        }

        sessionAuthenticationStrategy.onAuthentication(authentication, http, response);
        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(authentication);
        holder.setContext(context);
        securityContextRepository.saveContext(context, http, response);

        Account account = accountService.recordLogin(((AccountPrincipal) authentication.getPrincipal()).getAccountId());
        log.info("signed in: {} ({})", account.getEmail(), account.effectiveRole());
        return SessionUserDto.of(account);
    }
}
