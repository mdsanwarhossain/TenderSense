package com.bracit.tendersense.controller;

import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.MatcherType;
import com.bracit.tendersense.repository.AccountRepository;
import com.bracit.tendersense.repository.MatchResultRepository;
import com.bracit.tendersense.repository.OrganisationRepository;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.support.TestAuth;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Hits the endpoints the way the browser does.
 *
 * <p>Exists because two separate 500s reached a running server through lazy-loading:
 * {@code CapabilityProfile.pastProjects} and {@code EligibilityVerdict.gaps}. Neither
 * was visible in a service-level test, because those run inside a transaction while a
 * controller serialises its response after the transaction has closed
 * ({@code open-in-view} is off). Only a request-level test catches that class of bug.
 */
@SpringBootTest(properties = "tendersense.source.mode=cached")
@AutoConfigureMockMvc
class ApiEndpointsIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private OrganisationRepository organisationRepository;
    @Autowired
    private TenderRepository tenderRepository;
    @Autowired
    private MatchResultRepository matchResultRepository;

    private RequestPostProcessor as(String slug) {
        Organisation org = organisationRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalStateException("seeded company missing: " + slug));
        return TestAuth.company(accountRepository, org);
    }

    private RequestPostProcessor bracit() {
        return as("bracit");
    }

    /**
     * The top-ranked tender, i.e. one that definitely has a match result and an
     * eligibility verdict. An arbitrary tender may have neither, in which case the
     * lazy-loading paths this class exists to guard are never executed.
     */
    private Long topRankedTenderId() throws Exception {
        String json = mockMvc.perform(get("/api/tenders").param("size", "1").with(bracit()))
                .andReturn().getResponse().getContentAsString();
        int idx = json.indexOf("\"id\":");
        if (idx < 0) {
            return null;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = idx + 5; i < json.length() && Character.isDigit(json.charAt(i)); i++) {
            digits.append(json.charAt(i));
        }
        return digits.isEmpty() ? null : Long.parseLong(digits.toString());
    }

    @Test
    @DisplayName("GET /api/tenders serialises a full page without lazy-loading failures")
    void listsTenders() throws Exception {
        mockMvc.perform(get("/api/tenders").param("size", "60").param("page", "0").with(bracit()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    @DisplayName("?sort=NEWEST returns the newest tenders first; the default still ranks by score")
    void sortsByNewest() throws Exception {
        Organisation bracit = organisationRepository.findBySlug("bracit").orElseThrow();

        // The list never returns publishedAt, so the expected order is taken from the
        // database and compared by id. That still exercises the real thing: the sort
        // property path "tender.publishedAt" and its nulls-last handling.
        List<Long> expected = tenderRepository.findAll(
                        Sort.by(Sort.Order.desc("publishedAt").nullsLast(), Sort.Order.desc("id")))
                .stream()
                .map(Tender::getId)
                .filter(id -> matchResultRepository.findByTenderIdAndOrganisationIdAndMatcherType(
                        id, bracit.getId(), MatcherType.EMBEDDING).isPresent())
                .toList();
        Assumptions.assumeTrue(expected.size() >= 3, "not enough scored tenders to order");

        List<Long> actual = ids(mockMvc.perform(get("/api/tenders")
                        .param("size", "10").param("sort", "NEWEST").param("includeClosed", "true")
                        .with(bracit()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertTrue(actual.size() >= 3, "expected a page of rows, got " + actual.size());
        assertEquals(expected.subList(0, actual.size()), actual,
                "?sort=NEWEST did not return the newest tenders first");

        // The list's reason for existing: without the parameter, best match first.
        List<Double> scores = scores(mockMvc.perform(get("/api/tenders").param("size", "10").with(bracit()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (int i = 1; i < scores.size(); i++) {
            assertTrue(scores.get(i - 1) >= scores.get(i),
                    "the default order is no longer by score: " + scores);
        }
    }

    private static List<Long> ids(String json) {
        List<Long> out = new ArrayList<>();
        Matcher m = Pattern.compile("[{,]" + quoted("id") + ":([0-9]+)").matcher(json);
        while (m.find()) {
            out.add(Long.valueOf(m.group(1)));
        }
        return out;
    }

    private static List<Double> scores(String json) {
        List<Double> out = new ArrayList<>();
        Matcher m = Pattern.compile(quoted("score") + ":([0-9.eE+-]+)").matcher(json);
        while (m.find()) {
            out.add(Double.valueOf(m.group(1)));
        }
        return out;
    }

    /** A JSON field name in quotes, built without stacking backslashes. */
    private static String quoted(String field) {
        String q = String.valueOf((char) 34);
        return q + field + q;
    }

    @Test
    @DisplayName("GET /api/profile serialises nested projects and certifications")
    void readsProfile() throws Exception {
        mockMvc.perform(get("/api/profile").with(bracit()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services").isArray())
                .andExpect(jsonPath("$.pastProjects").isArray())
                .andExpect(jsonPath("$.certifications").isArray());
    }

    @Test
    @DisplayName("tender detail, evidence and eligibility all serialise")
    void readsTenderDetailEndpoints() throws Exception {
        Long id = topRankedTenderId();
        Assumptions.assumeTrue(id != null, "no scored tenders ingested");

        mockMvc.perform(get("/api/tenders/{id}", id).with(bracit()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").exists());

        mockMvc.perform(get("/api/tenders/{id}/evidence", id).with(bracit()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence").isArray())
                .andExpect(jsonPath("$.grade").exists())
                .andExpect(jsonPath("$.recommendation").exists());

        // Reads the gaps collection: the exact path that threw before the fetch join.
        mockMvc.perform(get("/api/tenders/{id}/eligibility", id).with(bracit()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.gaps").isArray());
    }

    @Test
    @DisplayName("benchmark serialises for a company; pipeline runs page for an admin")
    void readsBenchmarkAndRuns() throws Exception {
        mockMvc.perform(get("/api/benchmark").with(bracit()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semantic.matcher").value("EMBEDDING"))
                .andExpect(jsonPath("$.keyword.matcher").value("KEYWORD"))
                .andExpect(jsonPath("$.caveat").isNotEmpty());

        mockMvc.perform(get("/api/pipeline/runs").param("size", "5").with(TestAuth.admin(accountRepository)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    @DisplayName("unknown tender returns 404, not 500")
    void unknownTenderIsNotFound() throws Exception {
        mockMvc.perform(get("/api/tenders/{id}", 99_999_999L).with(bracit()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("two companies signed in to the same URL get disjoint shortlists")
    void eachCompanySeesItsOwnShortlist() throws Exception {
        List<Long> bracit = topTenIds("bracit");
        List<Long> padma = topTenIds("padma-infra");
        Assumptions.assumeFalse(bracit.isEmpty() || padma.isEmpty(), "a scored corpus is required");

        List<Long> overlap = bracit.stream().filter(padma::contains).toList();
        System.out.printf("top-10 overlap between the two organisations: %d%n", overlap.size());
        assertTrue(overlap.isEmpty(),
                "the two companies' top tenders overlap -- the sign-in is not scoping the query");
    }

    private List<Long> topTenIds(String slug) throws Exception {
        String json = mockMvc.perform(get("/api/tenders").param("size", "10").with(as(slug)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Long> ids = new ArrayList<>();
        Matcher m = Pattern.compile("\\{\"id\":(\\d+)").matcher(json);
        while (m.find()) {
            ids.add(Long.parseLong(m.group(1)));
        }
        return ids;
    }
}
