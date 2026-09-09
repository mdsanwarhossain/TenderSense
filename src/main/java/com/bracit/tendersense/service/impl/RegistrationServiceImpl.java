package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.SignupRequest;
import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.Sector;
import com.bracit.tendersense.repository.CapabilityProfileRepository;
import com.bracit.tendersense.repository.OrganisationRepository;
import com.bracit.tendersense.service.AccountService;
import com.bracit.tendersense.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationServiceImpl implements RegistrationService {

    private static final int MIN_PASSWORD = 8;
    private static final int MAX_SLUG = 64;
    /** Deliberately permissive: rejecting unusual but valid addresses is the worse error. */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]+$");

    private final OrganisationRepository organisationRepository;
    private final CapabilityProfileRepository profileRepository;
    private final AccountService accountService;

    @Override
    @Transactional
    public Organisation register(SignupRequest request) {
        String name = trimmed(request.companyName());
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Enter your company name");
        }
        String email = trimmed(request.email()).toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Enter a valid email address");
        }
        if (accountService.emailTaken(email)) {
            throw new IllegalArgumentException("That email already has an account -- sign in instead");
        }
        String password = request.password() == null ? "" : request.password();
        if (password.length() < MIN_PASSWORD) {
            throw new IllegalArgumentException(
                    "Use a password of at least " + MIN_PASSWORD + " characters");
        }
        List<Sector> sectors = request.sectors() == null ? List.of() : request.sectors();
        if (sectors.isEmpty()) {
            // Without sectors the gate admits nothing, so the new company would sign in to
            // an empty shortlist and reasonably conclude the product is broken.
            throw new IllegalArgumentException("Choose at least one sector you bid in");
        }

        Organisation organisation = organisationRepository.save(Organisation.builder()
                .name(name)
                .slug(uniqueSlug(name))
                .description(null)
                .sectors(new ArrayList<>(sectors.stream().distinct().toList()))
                .active(true)
                .demonstration(false)
                .createdAt(Instant.now())
                .build());

        accountService.createFor(organisation, email, password);

        // An empty profile, not a copy of anyone else's: the company describes its own work
        // next, on the profile editor it lands on.
        profileRepository.save(CapabilityProfile.builder()
                .organisation(organisation)
                .orgName(name)
                .summary(null)
                .services(new ArrayList<>())
                .exclusions(new ArrayList<>())
                .geographies(new ArrayList<>(List.of("Bangladesh")))
                .pastProjects(new ArrayList<>())
                .certifications(new ArrayList<>())
                .updatedAt(Instant.now())
                .build());

        log.info("registered company {} ({}) with {} sectors",
                organisation.getSlug(), email, sectors.size());
        return organisation;
    }

    /**
     * A readable slug derived from the name, with a numeric suffix when taken. Companies
     * pick their own names and two of them may well collide.
     */
    private String uniqueSlug(String name) {
        String base = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (base.isEmpty()) {
            base = "company";
        }
        if (base.length() > MAX_SLUG - 4) {
            base = base.substring(0, MAX_SLUG - 4);
        }
        String candidate = base;
        for (int n = 2; organisationRepository.findBySlug(candidate).isPresent(); n++) {
            candidate = base + "-" + n;
        }
        return candidate;
    }

    private static String trimmed(String s) {
        return s == null ? "" : s.trim();
    }
}
