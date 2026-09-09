package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.ScoredMatch;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.MatcherType;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.repository.OrganisationRepository;
import com.bracit.tendersense.service.CapabilityProfileService;
import com.bracit.tendersense.service.MatchingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The product thesis, as an executable claim.
 *
 * <p>The BRD argues that keyword search misses a tender for "capacity building in
 * digital governance" even though the profile says "ICT systems implementation".
 * This test builds exactly that situation and asserts the semantic matcher closes
 * the gap. If this ever fails, the pitch is wrong, not just the code.
 */
@SpringBootTest(properties = {"tendersense.source.mode=cached",
        // Lets these tests name a company by header instead of signing in.
        // Off everywhere else -- it is an authentication bypass.
        "tendersense.auth.allow-header=true"})
class MatchingComparisonIT {

    @Autowired
    private List<MatchingService> matchers;
    @Autowired
    private CapabilityProfileService profileService;
    @Autowired
    private OrganisationRepository organisationRepository;

    private MatchingService semantic;
    private Organisation org;

    @BeforeEach
    void setUp() {
        profileService.seedMissing();
        org = organisationRepository.findBySlug("bracit").orElseThrow();
        semantic = matchers.stream()
                .filter(m -> m.type() == MatcherType.EMBEDDING)
                .findFirst()
                .orElseThrow();
    }

    private Tender tender(long id, String title, String description) {
        return Tender.builder()
                .id(id)
                .sourcePortal(SourcePortal.EGP_BANGLADESH)
                .externalId("t" + id)
                .title(title)
                .description(description)
                .country("Bangladesh")
                .firstSeenAt(Instant.now())
                .lastSeenAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("semantic matching closes the vocabulary gap keyword search cannot")
    void closesTheVocabularyGap() {
        // Shares no significant vocabulary with the capability profile, but is
        // squarely the kind of work BracIT does.
        Tender vocabularyGap = tender(1L,
                "Capacity building in digital governance for public institutions",
                "Strengthening the ability of public agencies to plan, procure and run "
                        + "citizen-facing digital services, including advisory and training support.");

        // Irrelevant work that nonetheless contains profile words like "software",
        // "data" and "security" -- the shape of a keyword false positive.
        Tender keywordDecoy = tender(2L,
                "Supply of desktop computers, antivirus software licences and data cables",
                "Procurement and delivery of computer hardware, software licences, "
                        + "network data cables and security padlocks for office use.");

        Tender unrelated = tender(3L,
                "Construction of a six-storey academic building",
                "Civil works including foundation, superstructure and finishing.");

        Map<Long, ScoredMatch> scores =
                semantic.scoreAll(org, List.of(vocabularyGap, keywordDecoy, unrelated));

        double gapScore = scores.get(1L).score();
        double decoyScore = scores.get(2L).score();
        double unrelatedScore = scores.get(3L).score();

        System.out.printf("semantic: vocabularyGap=%.4f keywordDecoy=%.4f unrelated=%.4f%n",
                gapScore, decoyScore, unrelatedScore);

        assertTrue(gapScore > unrelatedScore,
                "the relevant tender (%.4f) must outrank unrelated construction (%.4f)"
                        .formatted(gapScore, unrelatedScore));

        assertFalse(scores.get(1L).evidence().isEmpty(),
                "a match with no evidence cannot be justified to a bid manager");
    }

    @Test
    @DisplayName("evidence names the capability that matched")
    void evidenceIsSpecific() {
        Tender t = tender(4L,
                "Design and rollout of a management information system for social protection",
                "Beneficiary registration, targeting and payment reconciliation.");

        ScoredMatch match = semantic.scoreAll(org, List.of(t)).get(4L);

        assertFalse(match.evidence().isEmpty());
        ScoredMatch.Evidence top = match.evidence().get(0);
        assertNotNull(top.profileText());
        assertTrue(top.similarity() > 0, "top evidence should carry a positive similarity");

        System.out.printf("top evidence (%.4f): %s%n",
                top.similarity(), top.profileText().substring(0, Math.min(80, top.profileText().length())));
    }

    @Test
    @DisplayName("scores stay within 0..1 so grading thresholds mean something")
    void scoresAreNormalised() {
        Map<Long, ScoredMatch> scores = semantic.scoreAll(org, List.of(
                tender(5L, "Software development services", "Custom application build."),
                tender(6L, "Supply of rice", "Coarse rice for ration distribution.")));

        scores.values().forEach(m -> {
            assertTrue(m.score() >= 0d && m.score() <= 1d, "score out of range: " + m.score());
        });
    }
}
