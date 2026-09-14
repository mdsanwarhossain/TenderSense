package com.bracit.tendersense.controller;

import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.repository.AccountRepository;
import com.bracit.tendersense.repository.OrganisationRepository;
import com.bracit.tendersense.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The admin corpus list: every tender collected, with no company in the picture.
 */
@SpringBootTest(properties = "tendersense.source.mode=cached")
@AutoConfigureMockMvc
class AdminTendersIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OrganisationRepository organisationRepository;

    private RequestPostProcessor admin;

    @BeforeEach
    void setUp() {
        admin = TestAuth.admin(accountRepository);
    }

    @Test
    @DisplayName("lists the corpus a page at a time, newest first, with no score or tracking")
    void listsTheCorpus() throws Exception {
        mockMvc.perform(get("/api/admin/tenders").param("size", "5").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.content[0].id").isNumber())
                .andExpect(jsonPath("$.content[0].source").exists())
                // The things a company screen shows and this one must not.
                .andExpect(jsonPath("$.content[0].score").doesNotExist())
                .andExpect(jsonPath("$.content[0].grade").doesNotExist())
                .andExpect(jsonPath("$.content[0].wishlisted").doesNotExist())
                .andExpect(jsonPath("$.content[0].submitted").doesNotExist());
    }

    @Test
    @DisplayName("filters by portal, by what the model did, and by search term")
    void filters() throws Exception {
        mockMvc.perform(get("/api/admin/tenders").param("source", "EGP_BANGLADESH").param("size", "10").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].source", everyItem(is("EGP_BANGLADESH"))));

        mockMvc.perform(get("/api/admin/tenders").param("aiStatus", "DONE").param("size", "10").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].aiStatus", everyItem(is("DONE"))));

        // Open only: nothing already closed comes back.
        mockMvc.perform(get("/api/admin/tenders").param("includeClosed", "false").param("size", "20").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].closed", everyItem(is(false))));

        mockMvc.perform(get("/api/admin/tenders").param("search", "zzzznothingmatchesthis").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("a company account cannot read the corpus list")
    void companyIsRefused() throws Exception {
        Organisation bracit = organisationRepository.findBySlug("bracit").orElseThrow();
        mockMvc.perform(get("/api/admin/tenders").with(TestAuth.company(accountRepository, bracit)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/tenders"))
                .andExpect(status().isUnauthorized());
    }
}
