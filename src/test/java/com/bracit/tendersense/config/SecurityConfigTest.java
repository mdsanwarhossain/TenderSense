package com.bracit.tendersense.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What sign-in does to the session and the CSRF cookie.
 *
 * <p>A unit test on purpose: in the MockMvc tests, spring-security-test's {@code csrf()}
 * swaps the shared CsrfFilter onto a session-backed test repository for the rest of the
 * cached context, so no XSRF-TOKEN cookie is ever written there to check.
 */
class SecurityConfigTest {

    private static List<String> xsrfCookieValues(MockHttpServletResponse response) {
        return response.getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith("XSRF-TOKEN="))
                .map(h -> h.substring("XSRF-TOKEN=".length(), h.contains(";") ? h.indexOf(';') : h.length()))
                .toList();
    }

    @Test
    @DisplayName("sign-in gives a new session id and writes a fresh CSRF token into the same response")
    void signInRotatesSessionAndToken() {
        SessionAuthenticationStrategy strategy =
                new SecurityConfig().sessionAuthenticationStrategy(CookieCsrfTokenRepository.withHttpOnlyFalse());

        // The browser at sign-in: a session from before, and the token cookie it was handed.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setCookies(new Cookie("XSRF-TOKEN", "token-from-before"));
        String sessionBefore = request.getSession(true).getId();
        MockHttpServletResponse response = new MockHttpServletResponse();

        strategy.onAuthentication(UsernamePasswordAuthenticationToken.authenticated("someone", null, List.of()),
                request, response);

        assertNotEquals(sessionBefore, request.getSession().getId(),
                "the session id must change at sign-in (session fixation)");

        List<String> values = xsrfCookieValues(response);
        assertFalse(values.isEmpty(), "sign-in must re-issue the token cookie");
        String fresh = values.get(values.size() - 1);
        assertFalse(fresh.isEmpty(),
                "sign-in cleared the token without issuing a new one: a write straight after sign-in would be refused");
        assertNotEquals("token-from-before", fresh, "the token must rotate at sign-in");
    }
}
