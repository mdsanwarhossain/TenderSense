package com.bracit.tendersense.controller;

import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.repository.OrganisationRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Save for later and mark-as-submitted, through the endpoints the shortlist calls.
 *
 * <p>Shares the development database, so every tender this class touches has its
 * tracking rows snapshotted first and restored exactly afterwards -- a test run must not
 * wipe a tender the team really saved.
 *
 * <p>Same property set as ApiEndpointsIT, so Spring reuses that cached context instead of
 * starting (and downloading the embedding model for) a new one.
 */
@SpringBootTest(properties = {"tendersense.source.mode=cached",
        // Lets these tests name a company by header instead of signing in.
        // Off everywhere else -- it is an authentication bypass.
        "tendersense.auth.allow-header=true"})
@AutoConfigureMockMvc
class TrackingIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrganisationRepository organisationRepository;

    private Organisation bracit;
    private Organisation padma;
    private Long openTender;
    private List<Map<String, Object>> snapshot = List.of();
    private List<Long> touched = List.of();

    @BeforeEach
    void setUp() {
        bracit = organisationRepository.findBySlug("bracit").orElse(null);
        padma = organisationRepository.findBySlug("padma-infra").orElse(null);
        Assumptions.assumeTrue(bracit != null && padma != null, "both seeded companies required");

        openTender = firstTender("t.closing_at is null or t.closing_at > now() + interval '2 days'");
        Assumptions.assumeTrue(openTender != null, "a scored open tender is required");
        remember(openTender);
    }

    @AfterEach
    void restore() {
        for (Long id : touched) {
            jdbc.update("delete from tender_tracking where tender_id = ?", id);
        }
        for (Map<String, Object> r : snapshot) {
            jdbc.update("""
                    insert into tender_tracking (id, tender_id, organisation_id, wishlisted_at,
                                                 submitted_at, updated_at)
                    values (?, ?, ?, ?, ?, ?)""",
                    r.get("id"), r.get("tender_id"), r.get("organisation_id"),
                    r.get("wishlisted_at"), r.get("submitted_at"), r.get("updated_at"));
        }
    }

    /** Snapshot a tender's tracking rows, then clear them so the test starts clean. */
    private void remember(Long tenderId) {
        snapshot = new java.util.ArrayList<>(snapshot);
        snapshot.addAll(jdbc.queryForList("select * from tender_tracking where tender_id = ?", tenderId));
        touched = new java.util.ArrayList<>(touched);
        touched.add(tenderId);
        jdbc.update("delete from tender_tracking where tender_id = ?", tenderId);
    }

    /** A tender BracIT has an embedding score for -- i.e. one its shortlist can show. */
    private Long firstTender(String condition) {
        List<Long> ids = jdbc.queryForList("""
                select m.tender_id from match_result m join tender t on t.id = m.tender_id
                where m.organisation_id = ? and m.matcher_type = 'EMBEDDING' and (%s)
                order by m.score desc limit 1""".formatted(condition), Long.class, bracit.getId());
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String as(Organisation org) {
        return String.valueOf(org.getId());
    }

    @Test
    @DisplayName("saving twice leaves it saved, with the first save's time")
    void saveIsIdempotent() throws Exception {
        String first = mockMvc.perform(put("/api/tenders/{id}/wishlist", openTender).header("X-Org-Id", as(bracit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wishlisted").value(true))
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(put("/api/tenders/{id}/wishlist", openTender).header("X-Org-Id", as(bracit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wishlisted").value(true))
                .andReturn().getResponse().getContentAsString();

        assertEquals(first, second,
                "a repeated PUT must not flip the state or move the saved-at time");
    }

    @Test
    @DisplayName("DELETE clears it, and clearing twice is still fine")
    void unsave() throws Exception {
        mockMvc.perform(put("/api/tenders/{id}/wishlist", openTender).header("X-Org-Id", as(bracit)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/tenders/{id}/wishlist", openTender).header("X-Org-Id", as(bracit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wishlisted").value(false))
                .andExpect(jsonPath("$.wishlistedAt").doesNotExist());
        mockMvc.perform(delete("/api/tenders/{id}/wishlist", openTender).header("X-Org-Id", as(bracit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wishlisted").value(false));
    }

    @Test
    @DisplayName("submitted and saved are independent")
    void submittedIsIndependent() throws Exception {
        mockMvc.perform(put("/api/tenders/{id}/submission", openTender).header("X-Org-Id", as(bracit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submitted").value(true))
                .andExpect(jsonPath("$.submittedAt").exists())
                .andExpect(jsonPath("$.wishlisted").value(false));
    }

    @Test
    @DisplayName("tracking is per company: BracIT's save is invisible to Padma")
    void perCompany() throws Exception {
        mockMvc.perform(put("/api/tenders/{id}/wishlist", openTender).header("X-Org-Id", as(bracit)))
                .andExpect(status().isOk());

        String mine = mockMvc.perform(get("/api/tenders").param("tracked", "SAVED").param("size", "100")
                        .header("X-Org-Id", as(bracit)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String theirs = mockMvc.perform(get("/api/tenders").param("tracked", "SAVED").param("size", "100")
                        .header("X-Org-Id", as(padma)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String marker = "\"id\":" + openTender + ",";
        assertTrue(mine.contains(marker), "BracIT's Saved filter must include the tender it saved");
        assertTrue(mine.contains("\"wishlisted\":true"), "and the row must say so");
        assertFalse(theirs.contains(marker), "Padma must not see BracIT's saved tender");
    }

    @Test
    @DisplayName("the Submitted filter includes tenders that have since closed")
    void submittedIncludesClosed() throws Exception {
        Long closed = firstTender("t.closing_at < now() - interval '2 days'");
        Assumptions.assumeTrue(closed != null, "needs a scored tender that has closed");
        remember(closed);

        mockMvc.perform(put("/api/tenders/{id}/submission", closed).header("X-Org-Id", as(bracit)))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/tenders").param("tracked", "SUBMITTED").param("size", "100")
                        .header("X-Org-Id", as(bracit)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("\"id\":" + closed + ","),
                "a bid you submitted is usually past its deadline; the filter must still find it");
    }

    @Test
    @DisplayName("an unknown tender is a 404, not a tracking row pointing at nothing")
    void unknownTender() throws Exception {
        mockMvc.perform(put("/api/tenders/{id}/wishlist", 987654321L).header("X-Org-Id", as(bracit)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("each summary card's count equals what its filter returns")
    void summaryCountsMatchTheirFilters() throws Exception {
        String summary = summaryJson();
        long total = number(summary, "total");

        assertEquals(listCount(), total, "Total tenders must match the list's own total");
        assertEquals(listCount("grade", "S"), number(summary, "sGrade"),
                "Total S-grade must match the list filtered to S");
        assertEquals(listCount("closingSoon", "true"), number(summary, "closingSoon"),
                "Closing within 7 days must match the list filtered to closing soon");
        assertTrue(total > 25, "the corpus is bigger than one page, so a page count would differ");
    }

    @Test
    @DisplayName("the closing-soon filter and the row's amber flag are the same set")
    void closingSoonMatchesTheUrgentFlag() throws Exception {
        String soon = mockMvc.perform(get("/api/tenders").param("closingSoon", "true").param("size", "100")
                        .header("X-Org-Id", as(bracit)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(soon.contains("\"urgent\":false"),
                "a tender under 'Closing within 7 days' without the amber flag means the card and the row disagree");
    }

    @Test
    @DisplayName("the Saved and Submitted cards count this company's tracking, and move with it")
    void savedAndSubmittedCounts() throws Exception {
        String before = summaryJson();
        mockMvc.perform(put("/api/tenders/{id}/wishlist", openTender).header("X-Org-Id", as(bracit)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/tenders/{id}/submission", openTender).header("X-Org-Id", as(bracit)))
                .andExpect(status().isOk());
        String after = summaryJson();

        assertEquals(number(before, "saved") + 1, number(after, "saved"));
        assertEquals(number(before, "submitted") + 1, number(after, "submitted"));
        assertEquals(listCount("tracked", "SAVED"), number(after, "saved"), "Saved card must match the Saved filter");
        assertEquals(listCount("tracked", "SUBMITTED"), number(after, "submitted"),
                "Submitted card must match the Submitted filter");
    }

    private String summaryJson() throws Exception {
        return mockMvc.perform(get("/api/tenders/summary").header("X-Org-Id", as(bracit)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** The list's total for BracIT, with optional name/value filter pairs. */
    private long listCount(String... filter) throws Exception {
        var request = get("/api/tenders").param("size", "1").header("X-Org-Id", as(bracit));
        for (int i = 0; i + 1 < filter.length; i += 2) {
            request = request.param(filter[i], filter[i + 1]);
        }
        return number(mockMvc.perform(request).andReturn().getResponse().getContentAsString(),
                "totalElements");
    }

    private static long number(String json, String field) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + field + "\":(\\d+)").matcher(json);
        assertTrue(m.find(), field + " missing from " + json);
        return Long.parseLong(m.group(1));
    }

    @Test
    @DisplayName("every shortlist row carries a link to the tender's own portal page")
    void rowsCarrySourceUrl() throws Exception {
        mockMvc.perform(get("/api/tenders").param("size", "5").header("X-Org-Id", as(bracit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sourceUrl").value(org.hamcrest.Matchers.startsWith("https://")));
    }
}
