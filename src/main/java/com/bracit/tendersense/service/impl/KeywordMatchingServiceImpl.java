package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.ScoredMatch;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.MatcherType;
import com.bracit.tendersense.service.CapabilityProfileService;
import com.bracit.tendersense.service.MatchingService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * The keyword baseline the semantic matcher must beat.
 *
 * <p>This is PostgreSQL full-text search: the capability profile is reduced to its
 * distinct significant terms, OR-combined into a {@code tsquery}, and scored against
 * each tender with {@code ts_rank}. That is a fair representation of "what a good
 * keyword alert would do" -- term frequency and document length are accounted for,
 * rare terms weigh more than common ones.
 *
 * <p>It is deliberately competent. The BRD's headline claim is that semantic matching
 * beats keyword search; beating a crippled baseline would demonstrate nothing.
 *
 * <p>What it structurally cannot do is match "capacity building in digital governance"
 * to "ICT systems implementation", because those share no terms. That gap is the
 * product thesis, and it is what the benchmark is meant to expose.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeywordMatchingServiceImpl implements MatchingService {

    /** Terms shorter than this carry no discriminating power in tender text. */
    private static final int MIN_TERM_LENGTH = 4;

    /** Guards the tsquery against becoming unusably broad. */
    private static final int MAX_TERMS = 120;

    /**
     * ts_rank output is unbounded in principle but clusters low; this divisor maps
     * observed ranks onto roughly 0..1 without clipping the useful range flat.
     */
    private static final double RANK_NORMALISER = 0.5;

    /**
     * Domain-generic words that appear in most procurement notices. Leaving these in
     * would make the baseline match everything and look artificially weak.
     */
    private static final Set<String> STOP_TERMS = Set.of(
            "and", "the", "for", "with", "from", "into", "across", "including", "their",
            "other", "under", "over", "than", "that", "this", "these", "those", "which",
            "service", "services", "system", "systems", "support", "management",
            "development", "implementation", "solution", "solutions", "project", "projects",
            "programme", "program", "provision", "delivery", "based", "using", "work",
            "works", "national", "large", "several", "million", "years", "year");

    private final EntityManager entityManager;
    private final CapabilityProfileService profileService;

    private final Map<Long, String> queryByOrganisation = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public MatcherType type() {
        return MatcherType.KEYWORD;
    }

    @Override
    public String modelVersion() {
        return "postgres:ts_rank:english";
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ScoredMatch> scoreAll(Organisation organisation, List<Tender> tenders) {
        if (tenders.isEmpty()) {
            return Map.of();
        }
        String tsquery = queryTerms(organisation);
        if (tsquery.isBlank()) {
            return Map.of();
        }

        List<Long> ids = tenders.stream().map(Tender::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        // One statement for the whole batch: per-tender queries would be thousands
        // of round trips for a single scoring pass.
        Query query = entityManager.createNativeQuery("""
                SELECT t.id,
                       ts_rank(
                         to_tsvector('english',
                           coalesce(t.title, '') || ' ' || coalesce(t.description, '')),
                         to_tsquery('english', :terms)
                       ) AS rank
                FROM tender t
                WHERE t.id IN (:ids)
                """);
        query.setParameter("terms", tsquery);
        query.setParameter("ids", ids);

        Map<Long, ScoredMatch> out = new LinkedHashMap<>();
        for (Object row : query.getResultList()) {
            Object[] cells = (Object[]) row;
            Long id = ((Number) cells[0]).longValue();
            double rank = cells[1] == null ? 0d : ((Number) cells[1]).doubleValue();
            double normalised = Math.min(1d, rank / RANK_NORMALISER);
            out.put(id, new ScoredMatch(normalised, List.of()));
        }

        // Tenders the query matched nothing in still need an explicit zero, or they
        // would silently drop out of the benchmark rather than scoring badly.
        for (Long id : ids) {
            out.putIfAbsent(id, ScoredMatch.zero());
        }
        return out;
    }

    private String queryTerms(Organisation organisation) {
        return queryByOrganisation.computeIfAbsent(organisation.getId(),
                id -> buildQuery(organisation));
    }

    private String buildQuery(Organisation organisation) {
        Set<String> terms = new LinkedHashSet<>();
        for (String statement : profileService.capabilityStatements(organisation)) {
            for (String raw : statement.toLowerCase(Locale.ENGLISH).split("[^a-z0-9]+")) {
                if (raw.length() >= MIN_TERM_LENGTH && !STOP_TERMS.contains(raw)) {
                    terms.add(raw);
                }
                if (terms.size() >= MAX_TERMS) {
                    break;
                }
            }
        }
        String query = String.join(" | ", terms);
        log.info("keyword baseline for {} built from {} distinct profile terms",
                organisation.getSlug(), terms.size());
        return query;
    }

    @Override
    public void invalidate(Organisation organisation) {
        queryByOrganisation.remove(organisation.getId());
    }
}
