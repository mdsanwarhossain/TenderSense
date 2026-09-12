package com.bracit.tendersense.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The same ProblemDetail JSON that GlobalExceptionHandler returns, for responses written
 * by security filters -- which reject a request before any controller advice can run.
 * The frontend reads {@code detail} from both, so they must not differ.
 */
public final class ProblemResponses {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ProblemResponses() {
    }

    public static void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.writeValueAsString(body));
    }
}
