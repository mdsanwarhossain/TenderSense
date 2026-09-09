package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.Certification;
import com.bracit.tendersense.entity.PastProject;
import com.bracit.tendersense.exception.NotFoundException;
import com.bracit.tendersense.repository.CapabilityProfileRepository;
import com.bracit.tendersense.service.AccountService;
import com.bracit.tendersense.service.CapabilityProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CapabilityProfileServiceImpl implements CapabilityProfileService {

    /** Every profile resource seeds one organisation. */
    private static final List<String> PROFILE_RESOURCES = List.of(
            "data/capability-profile.json",
            "data/capability-profile-construction.json");

    private final CapabilityProfileRepository repository;
    private final AccountService accountService;
    private final com.bracit.tendersense.repository.OrganisationRepository organisationRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public CapabilityProfile forOrganisation(Organisation organisation) {
        return repository.findByOrganisationId(organisation.getId())
                .orElseThrow(() -> new NotFoundException(
                        "no capability profile for " + organisation.getSlug()));
    }

    /**
     * Each service line and each past project becomes its own statement. Past
     * projects contribute title plus description because the description carries
     * the domain vocabulary a tender is most likely to echo.
     */
    @Override
    public List<String> capabilityStatements(Organisation organisation) {
        CapabilityProfile p = forOrganisation(organisation);
        List<String> out = new ArrayList<>(p.getServices());
        for (PastProject project : p.getPastProjects()) {
            String text = project.getDescription() == null || project.getDescription().isBlank()
                    ? project.getTitle()
                    : project.getTitle() + ". " + project.getDescription();
            out.add(text);
        }
        if (p.getSummary() != null && !p.getSummary().isBlank()) {
            out.add(p.getSummary());
        }
        return out;
    }

    @Override
    public List<String> exclusionStatements(Organisation organisation) {
        return List.copyOf(forOrganisation(organisation).getExclusions());
    }

    @Override
    @Transactional
    public void seedMissing() {
        PROFILE_RESOURCES.forEach(this::seedOne);
    }

    /**
     * Seeds one bundled company, idempotently and per-company.
     *
     * <p>Deliberately not gated on a global "are there any profiles yet" count. That form
     * had two failure modes: a database holding companies from before a new seeded field
     * existed never received it, and -- once profiles became editable -- a single stored
     * profile would suppress seeding of a company that had not been created yet.
     *
     * <p>An existing company is never overwritten, because by then its profile may have
     * been edited in the product. Only genuinely missing pieces are filled in.
     */
    private void seedOne(String resource) {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            JsonNode root = objectMapper.readTree(in);

            String slug = root.path("slug").asString();
            String loginEmail = root.path("loginEmail").asString(null);

            Optional<Organisation> existing = organisationRepository.findBySlug(slug);
            if (existing.isPresent()) {
                // The company is already here. Give it an account if it predates sign-in,
                // and leave everything else exactly as it is.
                if (loginEmail != null) {
                    accountService.seedFor(existing.get(), loginEmail);
                }
                return;
            }

            List<com.bracit.tendersense.entity.enums.Sector> sectors = new ArrayList<>();
            root.path("sectors").forEach(n -> sectors.add(
                    com.bracit.tendersense.entity.enums.Sector.valueOf(n.asString())));

            Organisation organisation = organisationRepository.save(Organisation.builder()
                    .name(root.path("orgName").asString())
                    .slug(root.path("slug").asString())
                    .description(root.path("description").asString(null))
                    .sectors(sectors)
                    .active(true)
                    .demonstration(resource.contains("construction"))
                    .createdAt(Instant.now())
                    .build());

            // A seeded company with no account is unreachable the moment sign-in lands.
            if (loginEmail != null) {
                accountService.seedFor(organisation, loginEmail);
            }

            CapabilityProfile profile = CapabilityProfile.builder()
                    .organisation(organisation)
                    .orgName(root.path("orgName").asString())
                    .summary(root.path("summary").asString())
                    .annualTurnoverBdt(new BigDecimal(root.path("annualTurnoverBdt").asLong()))
                    .services(stringList(root.path("services")))
                    .exclusions(stringList(root.path("exclusions")))
                    .geographies(stringList(root.path("geographies")))
                    .updatedAt(Instant.now())
                    .build();

            for (JsonNode c : root.path("certifications")) {
                profile.getCertifications().add(Certification.builder()
                        .profile(profile)
                        .code(c.path("code").asString())
                        .name(c.path("name").asString())
                        .validUntil(parseDate(c.path("validUntil").asString(null)))
                        .build());
            }
            for (JsonNode pr : root.path("pastProjects")) {
                profile.getPastProjects().add(PastProject.builder()
                        .profile(profile)
                        .title(pr.path("title").asString())
                        .client(pr.path("client").asString(null))
                        .description(pr.path("description").asString(null))
                        .sector(pr.path("sector").asString(null))
                        .valueBdt(pr.hasNonNull("valueBdt")
                                ? new BigDecimal(pr.path("valueBdt").asLong()) : null)
                        .year(pr.hasNonNull("year") ? pr.path("year").asInt() : null)
                        .build());
            }

            repository.save(profile);
            log.info("Seeded {}: {} sectors, {} services, {} exclusions, {} projects, {} certs",
                    organisation.getSlug(), sectors.size(), profile.getServices().size(),
                    profile.getExclusions().size(), profile.getPastProjects().size(),
                    profile.getCertifications().size());
        } catch (Exception e) {
            log.error("could not seed {}: {}", resource, e.getMessage());
        }
    }

    private static List<String> stringList(JsonNode node) {
        List<String> out = new ArrayList<>();
        node.forEach(n -> out.add(n.asString()));
        return out;
    }

    private static LocalDate parseDate(String raw) {
        try {
            return raw == null ? null : LocalDate.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
