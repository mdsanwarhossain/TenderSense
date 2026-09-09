package com.bracit.tendersense.controller;

import com.bracit.tendersense.config.OrganisationArgumentResolver;
import com.bracit.tendersense.dto.LoginRequest;
import com.bracit.tendersense.dto.OrganisationDto;
import com.bracit.tendersense.dto.SignupRequest;
import com.bracit.tendersense.entity.Account;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.exception.UnauthenticatedException;
import com.bracit.tendersense.repository.OrganisationRepository;
import com.bracit.tendersense.service.AccountService;
import com.bracit.tendersense.service.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Sign in, sign out, and "who am I".
 *
 * <p>Session-based on purpose: no JWT, no token refresh, no client-side storage of
 * credentials. The browser holds a same-origin cookie and the server holds the mapping
 * from that cookie to an organisation id, which is the whole mechanism.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AccountService accountService;
    private final OrganisationRepository organisationRepository;
    private final RegistrationService registrationService;

    /** Registers a new company and signs it straight in. */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganisationDto signup(@RequestBody SignupRequest request, HttpServletRequest http) {
        Organisation organisation = registrationService.register(request);
        startSession(http, organisation.getId());
        return OrganisationDto.of(organisation);
    }

    @PostMapping("/login")
    public OrganisationDto login(@RequestBody LoginRequest request, HttpServletRequest http) {
        Account account = accountService.authenticate(request.email(), request.password());

        startSession(http, account.getOrganisation().getId());
        log.info("signed in: {}", account.getOrganisation().getSlug());
        return OrganisationDto.of(account.getOrganisation());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        // Always 204: whether there was a session to end is not the caller's business,
        // and a client cleaning up after an expired session should not see an error.
    }

    /** The signed-in company, or 401. The frontend calls this once at bootstrap. */
    @GetMapping("/me")
    public OrganisationDto me(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        Long orgId = session == null
                ? null
                : (Long) session.getAttribute(OrganisationArgumentResolver.SESSION_KEY);
        if (orgId == null) {
            throw new UnauthenticatedException("Not signed in");
        }
        Organisation organisation = organisationRepository.findById(orgId)
                .filter(Organisation::isActive)
                .orElseThrow(() -> {
                    session.invalidate();
                    return new UnauthenticatedException("Not signed in");
                });
        return OrganisationDto.of(organisation);
    }

    /**
     * Starts an authenticated session, discarding any anonymous one first so a session id
     * observed before sign-in cannot be replayed after it.
     */
    private static void startSession(HttpServletRequest http, Long organisationId) {
        HttpSession existing = http.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        http.getSession(true).setAttribute(
                OrganisationArgumentResolver.SESSION_KEY, organisationId);
    }
}
