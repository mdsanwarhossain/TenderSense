package com.bracit.tendersense.controller;

import com.bracit.tendersense.entity.Account;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.Role;
import com.bracit.tendersense.repository.AccountRepository;
import com.bracit.tendersense.repository.OrganisationRepository;
import com.bracit.tendersense.security.AccountPrincipal;
import com.bracit.tendersense.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Who may reach what. Each rule here is one a user would notice if it broke: a company
 * reaching the pipeline controls, an admin locked out of their own panel, a switched-off
 * account still browsing.
 *
 * <p>Shares the development database. The only rows it creates are throwaway admin
 * accounts, deleted afterwards; it never switches off a real company or account.
 */
@SpringBootTest(properties = "tendersense.source.mode=cached")
@AutoConfigureMockMvc
class SecurityIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OrganisationRepository organisationRepository;

    private RequestPostProcessor company;
    private RequestPostProcessor admin;
    private final List<String> created = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Organisation bracit = organisationRepository.findBySlug("bracit").orElseThrow();
        company = TestAuth.company(accountRepository, bracit);
        admin = TestAuth.admin(accountRepository);
    }

    @AfterEach
    void cleanUp() {
        created.forEach(email -> accountRepository.findByEmail(email).ifPresent(accountRepository::delete));
    }

    @Test
    @DisplayName("signed out: every API but sign-in answers 401")
    void signedOut() throws Exception {
        for (String url : List.of("/api/tenders", "/api/dashboard", "/api/admin/dashboard",
                "/api/pipeline/runs", "/api/pipeline/processing")) {
            mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
        // Sign-up lists sectors before an account exists.
        mockMvc.perform(get("/api/sectors")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a company account reaches its own screens, never the admin panel or pipeline")
    void companyAccount() throws Exception {
        mockMvc.perform(get("/api/dashboard").with(company))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numbers.open").isNumber())
                .andExpect(jsonPath("$.bestMatches").isArray())
                .andExpect(jsonPath("$.activity.scoring.stale").isBoolean());
        mockMvc.perform(get("/api/pipeline/digest").with(company)).andExpect(status().isOk());

        for (String url : List.of("/api/admin/dashboard", "/api/admin/users", "/api/admin/schedule", "/api/pipeline/runs",
                "/api/pipeline/schedule", "/api/pipeline/processing")) {
            mockMvc.perform(get(url).with(company))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value("You do not have access to this."));
        }
        mockMvc.perform(post("/api/pipeline/run").with(company)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/pipeline/rescore-all").with(company)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the platform admin reaches the admin panel and pipeline, but has no company screens")
    void adminAccount() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies.total").isNumber())
                .andExpect(jsonPath("$.corpus").isArray())
                .andExpect(jsonPath("$.grades").isArray())
                .andExpect(jsonPath("$.schedule.jobs.length()").value(7))
                .andExpect(jsonPath("$.processing").exists());
        mockMvc.perform(get("/api/admin/companies").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == 'bracit')].accountEmail").value("tenders@bracits.com"));
        mockMvc.perform(get("/api/admin/users").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("ADMIN"));
        mockMvc.perform(get("/api/pipeline/runs/summary").with(admin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/pipeline/schedule").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[0].key").value("egpDiscovery"));

        // No company, so nothing to scope a tender list to.
        mockMvc.perform(get("/api/tenders").with(admin)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a write without the CSRF token is refused, even when signed in")
    void writesNeedCsrf() throws Exception {
        Account account = accountRepository.findByEmail("tenders@bracits.com").orElseThrow();
        mockMvc.perform(put("/api/tenders/{id}/wishlist", 1L).with(user(AccountPrincipal.forSession(account))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("security token")));
    }

    @Test
    @DisplayName("an admin cannot demote or switch off themselves")
    void cannotLockYourselfOut() throws Exception {
        Long me = accountRepository.findFirstByRoleOrderByIdAsc(Role.ADMIN).orElseThrow().getId();
        mockMvc.perform(patch("/api/admin/users/{id}", me).with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("your own")));
        mockMvc.perform(patch("/api/admin/users/{id}", me).with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an account switched off by an admin is signed out on its very next request")
    void switchedOffTakesEffectImmediately() throws Exception {
        String email = "it-admin-" + System.nanoTime() + "@tendersense.local";
        created.add(email);
        mockMvc.perform(post("/api/admin/users").with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"long-enough\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        Account other = accountRepository.findByEmail(email).orElseThrow();
        // Held from before the switch-off, exactly as a browser session would be.
        RequestPostProcessor theirSession = TestAuth.as(other);
        mockMvc.perform(get("/api/admin/dashboard").with(theirSession)).andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/users/{id}", other.getId()).with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(get("/api/admin/dashboard").with(theirSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("switched off")));
    }

    @Test
    @DisplayName("password rules and duplicate emails come back as plain 400 messages")
    void adminInputIsValidated() throws Exception {
        mockMvc.perform(post("/api/admin/users").with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"tenders@bracits.com\",\"password\":\"long-enough\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("That email already has an account."));
        Long bracitAccount = accountRepository.findByEmail("tenders@bracits.com").orElseThrow().getId();
        mockMvc.perform(post("/api/admin/users/{id}/password", bracitAccount).with(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Passwords need at least 8 characters."));
    }
}
