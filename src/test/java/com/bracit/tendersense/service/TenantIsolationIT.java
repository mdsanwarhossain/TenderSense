package com.bracit.tendersense.service;

import com.bracit.tendersense.entity.MatchResult;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.MatcherType;
import com.bracit.tendersense.entity.enums.Sector;
import com.bracit.tendersense.repository.MatchResultRepository;
import com.bracit.tendersense.repository.OrganisationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The test the multi-tenant refactor exists for.
 *
 * <p>Before this refactor a tender had exactly one score, because both matchers cached a
 * single capability profile in instance fields and {@code match_result} was unique on
 * {@code (tender, matcher)}. Scoring a second company overwrote the first one's numbers.
 * These assertions fail loudly if any of that comes back.
 */
@SpringBootTest(properties = {"tendersense.source.mode=cached",
        // Lets these tests name a company by header instead of signing in.
        // Off everywhere else -- it is an authentication bypass.
        "tendersense.auth.allow-header=true"})
@Transactional(readOnly = true)
class TenantIsolationIT {

    @Autowired
    private OrganisationRepository organisationRepository;
    @Autowired
    private MatchResultRepository matchResultRepository;
    @Autowired
    private EntityManager em;

    private Organisation it;
    private Organisation construction;

    @BeforeEach
    void setUp() {
        it = organisationRepository.findBySlug("bracit").orElse(null);
        construction = organisationRepository.findBySlug("padma-infra").orElse(null);
        Assumptions.assumeTrue(it != null && construction != null,
                "both seeded organisations required");
        Assumptions.assumeTrue(
                matchResultRepository.countByOrganisationId(it.getId()) > 0
                        && matchResultRepository.countByOrganisationId(construction.getId()) > 0,
                "a scored corpus is required -- run the pipeline first");
    }

    @Test
    @DisplayName("the two companies subscribe to disjoint sectors")
    void sectorsAreDisjoint() {
        assertFalse(it.getSectors().isEmpty());
        assertFalse(construction.getSectors().isEmpty());
        assertTrue(it.getSectors().stream().noneMatch(construction.getSectors()::contains),
                "the demonstration value of the second tenant depends on the contrast");
    }

    @Test
    @DisplayName("both companies hold scores, and neither overwrote the other")
    void bothOrganisationsHaveTheirOwnRows() {
        long itRows = matchResultRepository.countByOrganisationId(it.getId());
        long constructionRows = matchResultRepository.countByOrganisationId(construction.getId());

        System.out.printf("match_result rows -- %s=%d, %s=%d%n",
                it.getSlug(), itRows, construction.getSlug(), constructionRows);

        assertTrue(itRows > 0 && constructionRows > 0);
    }

    /**
     * The one that would have been impossible before: a tender both companies can see --
     * i.e. one classified {@code OTHER}, which is ungated -- carries two independent
     * scores. If the unique key were still {@code (tender, matcher)} only one row could
     * exist and this could never pass.
     */
    @Test
    @DisplayName("a shared tender carries a different score for each company")
    void sameTenderTwoScores() {
        List<Long> shared = em.createQuery("""
                        select m.tender.id from MatchResult m
                        where m.matcherType = :type and m.organisation.id in (:a, :b)
                        group by m.tender.id having count(distinct m.organisation.id) = 2
                        """, Long.class)
                .setParameter("type", MatcherType.EMBEDDING)
                .setParameter("a", it.getId())
                .setParameter("b", construction.getId())
                .setMaxResults(50)
                .getResultList();

        Assumptions.assumeFalse(shared.isEmpty(), "no ungated tender in the corpus");

        int differing = 0;
        for (Long tenderId : shared) {
            double itScore = scoreOf(tenderId, it);
            double constructionScore = scoreOf(tenderId, construction);
            if (Math.abs(itScore - constructionScore) > 1e-6) {
                differing++;
            }
        }

        System.out.printf("%d of %d shared tenders scored differently by the two profiles%n",
                differing, shared.size());
        assertTrue(differing > shared.size() / 2,
                "the two profiles produced near-identical scores -- one profile is probably "
                        + "being used for both organisations");
    }

    /**
     * The hard gate, asserted from both sides: a construction tender is scored for the
     * construction firm and not for the IT firm, and -- the part that makes the gate
     * recoverable rather than lossy -- the tender is still in {@code tender} with its
     * sector tag, so correcting a tag surfaces it without a re-scrape.
     */
    @Test
    @DisplayName("the sector gate withholds scores but never discards tenders")
    void gateIsRecoverable() {
        List<Tender> constructionTenders = em.createQuery(
                        "select t from Tender t where t.sector = :s", Tender.class)
                .setParameter("s", Sector.CONSTRUCTION)
                .setMaxResults(200)
                .getResultList();

        Assumptions.assumeFalse(constructionTenders.isEmpty(), "no CONSTRUCTION tender stored");
        assertFalse(it.getSectors().contains(Sector.CONSTRUCTION),
                "precondition: the IT firm does not subscribe to CONSTRUCTION");

        for (Tender t : constructionTenders) {
            assertTrue(matchResultRepository
                            .findByTenderIdAndOrganisationIdAndMatcherType(
                                    t.getId(), it.getId(), MatcherType.EMBEDDING)
                            .isEmpty(),
                    "CONSTRUCTION tender %d leaked past the gate into %s"
                            .formatted(t.getId(), it.getSlug()));
            // Still stored, still tagged: the gate hides, it does not delete.
            assertEquals(Sector.CONSTRUCTION, t.getSector());
        }

        long scoredForConstructionFirm = constructionTenders.stream()
                .filter(t -> matchResultRepository
                        .findByTenderIdAndOrganisationIdAndMatcherType(
                                t.getId(), construction.getId(), MatcherType.EMBEDDING)
                        .isPresent())
                .count();

        System.out.printf("%d CONSTRUCTION tenders: 0 scored for %s, %d scored for %s%n",
                constructionTenders.size(), it.getSlug(),
                scoredForConstructionFirm, construction.getSlug());

        assertTrue(scoredForConstructionFirm > 0,
                "the construction firm should be scoring its own sector");
    }

    private double scoreOf(Long tenderId, Organisation org) {
        Optional<MatchResult> m = matchResultRepository
                .findByTenderIdAndOrganisationIdAndMatcherType(
                        tenderId, org.getId(), MatcherType.EMBEDDING);
        return m.map(MatchResult::getScore).orElseThrow();
    }
}
