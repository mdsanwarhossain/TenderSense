package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.Certification;
import com.bracit.tendersense.entity.PastProject;
import com.bracit.tendersense.exception.NotFoundException;
import com.bracit.tendersense.repository.CapabilityProfileRepository;
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
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CapabilityProfileServiceImpl implements CapabilityProfileService {

    private static final String PROFILE_RESOURCE = "data/capability-profile.json";

    private final CapabilityProfileRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public CapabilityProfile current() {
        return repository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new NotFoundException(
                        "no capability profile configured - seed one before scoring"));
    }

    /**
     * Each service line and each past project becomes its own statement. Past
     * projects contribute title plus description because the description carries
     * the domain vocabulary a tender is most likely to echo.
     */
    @Override
    public List<String> capabilityStatements() {
        CapabilityProfile p = current();
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
    public List<String> exclusionStatements() {
        return List.copyOf(current().getExclusions());
    }

    @Override
    @Transactional
    public void seedIfEmpty() {
        if (repository.count() > 0) {
            return;
        }
        try (InputStream in = new ClassPathResource(PROFILE_RESOURCE).getInputStream()) {
            JsonNode root = objectMapper.readTree(in);

            CapabilityProfile profile = CapabilityProfile.builder()
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
            log.info("Seeded capability profile: {} services, {} exclusions, {} projects, {} certs",
                    profile.getServices().size(), profile.getExclusions().size(),
                    profile.getPastProjects().size(), profile.getCertifications().size());
        } catch (Exception e) {
            log.error("could not seed capability profile: {}", e.getMessage());
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
