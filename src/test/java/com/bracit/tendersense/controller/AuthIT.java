package com.bracit.tendersense.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sign-in through Spring Security, including the parts that are easy to get subtly wrong.
 * These go through the real login endpoint and session -- nothing here is faked.
 */
@SpringBootTest(properties = "tendersense.source.mode=cached")
@AutoConfigureMockMvc
class AuthIT {

    private static final String EMAIL = "tenders@bracits.com";
    private static final String PASSWORD = "tendersense";

    @Autowired
    private MockMvc mockMvc;

    private String body(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private MockHttpSession signIn() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.organisation.slug").value("bracit"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    @DisplayName("a signed-out request is refused, not silently given a company")
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/tenders").param("size", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Not signed in"));
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("signing in opens the tender list and /me reports the account and company")
    void signInThenRead() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.organisation.slug").value("bracit"))
                .andExpect(jsonPath("$.organisation.sectors").isArray());

        mockMvc.perform(get("/api/tenders").param("size", "1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("a wrong password and an unknown email are indistinguishable")
    void doesNotRevealWhichAccountsExist() throws Exception {
        String wrongPassword = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(EMAIL, "not-the-password")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("nobody@example.com", "not-the-password")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertEquals(wrongPassword, unknownEmail,
                "the two failures must be indistinguishable, or this endpoint tells an "
                        + "attacker which companies are registered");
    }

    @Test
    @DisplayName("email case and surrounding space do not matter")
    void emailIsNormalised() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("  Tenders@BracITs.com  ", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organisation.slug").value("bracit"));
    }

    @Test
    @DisplayName("a sign-in without the CSRF token is refused")
    void signInNeedsCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(EMAIL, PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("signing out ends the session; logging out twice is still fine")
    void signOut() throws Exception {
        MockHttpSession session = signIn();

        mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/tenders").param("size", "1").session(session))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("the two seeded companies sign in to their own data")
    void bothSeededCompaniesWork() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("bids@padma-infra.com", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organisation.slug").value("padma-infra"))
                .andExpect(jsonPath("$.organisation.demonstration").value(true));
    }

    @Test
    @DisplayName("the seeded platform admin signs in with no company")
    void adminSignsIn() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("admin@tendersense.local", "tendersense-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.organisation").isEmpty());
    }
}
