package com.bracit.tendersense.controller;

import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.repository.TenderRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private TenderRepository tenderRepository;

    /**
     * The top-ranked tender, i.e. one that definitely has a match result and an
     * eligibility verdict. An arbitrary tender may have neither, in which case the
     * lazy-loading paths this class exists to guard are never executed.
     */
    private Long topRankedTenderId() throws Exception {
        String json = mockMvc.perform(get("/api/tenders").param("size", "1"))
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
        mockMvc.perform(get("/api/tenders").param("size", "60").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    @DisplayName("GET /api/profile serialises nested projects and certifications")
    void readsProfile() throws Exception {
        mockMvc.perform(get("/api/profile"))
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

        mockMvc.perform(get("/api/tenders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId").exists());

        mockMvc.perform(get("/api/tenders/{id}/evidence", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence").isArray())
                .andExpect(jsonPath("$.grade").exists())
                .andExpect(jsonPath("$.recommendation").exists());

        // Reads the gaps collection: the exact path that threw before the fetch join.
        mockMvc.perform(get("/api/tenders/{id}/eligibility", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.gaps").isArray());
    }

    @Test
    @DisplayName("benchmark and pipeline runs serialise")
    void readsBenchmarkAndRuns() throws Exception {
        mockMvc.perform(get("/api/benchmark"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semantic.matcher").value("EMBEDDING"))
                .andExpect(jsonPath("$.keyword.matcher").value("KEYWORD"))
                .andExpect(jsonPath("$.caveat").isNotEmpty());

        mockMvc.perform(get("/api/pipeline/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("unknown tender returns 404, not 500")
    void unknownTenderIsNotFound() throws Exception {
        mockMvc.perform(get("/api/tenders/{id}", 99_999_999L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the same URL with two X-Org-Id headers returns disjoint shortlists")
    void orgHeaderScopesTheShortlist() throws Exception {
        List<Long> bracit = topTenIds("1");
        List<Long> padma = topTenIds("2");
        Assumptions.assumeFalse(bracit.isEmpty() || padma.isEmpty(), "a scored corpus is required");

        List<Long> overlap = bracit.stream().filter(padma::contains).toList();
        System.out.printf("top-10 overlap between the two organisations: %d%n", overlap.size());
        assertTrue(overlap.isEmpty(),
                "the two companies' top tenders overlap -- the header is not scoping the query");
    }

    @Test
    @DisplayName("an omitted X-Org-Id falls back; an unknown one is a 404, not a silent fallback")
    void orgHeaderResolution() throws Exception {
        mockMvc.perform(get("/api/tenders").param("size", "1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/tenders").param("size", "1").header("X-Org-Id", "999999"))
                .andExpect(status().isNotFound());
    }

    private List<Long> topTenIds(String orgId) throws Exception {
        String json = mockMvc.perform(get("/api/tenders").param("size", "10")
                        .header("X-Org-Id", orgId))
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
