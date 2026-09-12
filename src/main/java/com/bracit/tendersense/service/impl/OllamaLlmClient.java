package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.LlmProperties;
import com.bracit.tendersense.dto.MatchComparison;
import com.bracit.tendersense.dto.MatchEvidenceResponse;
import com.bracit.tendersense.dto.TenderEnrichment;
import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.exception.LlmCallException;
import com.bracit.tendersense.exception.LlmUnavailableException;
import com.bracit.tendersense.service.LlmClient;
import com.bracit.tendersense.util.EnrichmentPrompt;
import com.bracit.tendersense.util.MatchSummaryPrompt;
import com.bracit.tendersense.util.MoneyTextParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama over its plain HTTP API.
 *
 * <p>Deliberately not the Spring AI Ollama starter: that starter registers a second
 * {@code EmbeddingModel}, and the MiniLM embedding injections then fail at startup. One
 * endpoint and a JSON body do not need a framework.
 */
@Service
@Slf4j
public class OllamaLlmClient implements LlmClient {

    private final LlmProperties props;
    private final RestClient http;
    /** Same server, shorter patience: someone is waiting on this one. */
    private final RestClient summaryHttp;
    private final ObjectMapper json = new ObjectMapper();

    public OllamaLlmClient(LlmProperties props) {
        this.props = props;
        this.http = client(props.getBaseUrl(), props.getReadTimeoutSeconds());
        this.summaryHttp = client(props.getBaseUrl(), props.getSummaryReadTimeoutSeconds());
    }

    private static RestClient client(String baseUrl, int readTimeoutSeconds) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public String model() {
        return props.getModel();
    }

    @Override
    public String summaryModel() {
        return props.getSummaryModel();
    }

    /**
     * The only model call a person waits for, so it is built to be quick and to give up
     * early: the small model, fewer threads than the batch job, and its own timeout well
     * under the batch ceiling. A caller that gets an exception falls back to the written
     * summary rather than showing nothing.
     */
    @Override
    public MatchComparison compare(Tender tender, CapabilityProfile profile,
                                   List<MatchEvidenceResponse.EvidencePair> evidence, int attempt) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", 0);
        options.put("seed", props.getSeed() + attempt);
        if (attempt > 0) {
            options.put("repeat_penalty", 1.3);
        }
        options.put("num_ctx", props.getNumCtx());
        options.put("num_thread", props.getSummaryNumThread());
        options.put("num_predict", props.getSummaryNumPredict());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getSummaryModel());
        body.put("stream", false);
        body.put("system", MatchSummaryPrompt.SYSTEM);
        body.put("prompt", MatchSummaryPrompt.user(tender, profile, evidence));
        body.put("format", MatchSummaryPrompt.schema());
        body.put("keep_alive", props.getKeepAlive());
        body.put("options", options);

        long started = System.currentTimeMillis();
        String raw = call(summaryHttp, body, props.getSummaryModel());
        log.debug("compared tender {} with {} in {} ms", tender.getExternalId(), profile.getOrgName(),
                System.currentTimeMillis() - started);
        return parseComparison(raw);
    }

    MatchComparison parseComparison(String raw) {
        JsonNode r = response(raw);
        return new MatchComparison(text(r, "comparison"), list(r, "matches"), list(r, "gaps"));
    }

    @Override
    public TenderEnrichment enrich(Tender tender, int attempt) {
        boolean wantTitle = EnrichmentPrompt.needsShortTitle(tender, props.getLongTitleChars());
        boolean wantRequirements = EnrichmentPrompt.hasEligibilityText(tender);

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", 0);
        options.put("seed", props.getSeed() + attempt);
        if (attempt > 0) {
            // Measured on a tender whose first answer looped ("Package 1111…"): a new seed
            // plus a repetition penalty gives a clean answer where a plain retry repeats.
            options.put("repeat_penalty", 1.3);
        }
        options.put("num_ctx", props.getNumCtx());
        options.put("num_thread", props.getNumThread());
        options.put("num_predict", 400);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getModel());
        body.put("stream", false);
        body.put("system", EnrichmentPrompt.SYSTEM);
        body.put("prompt", EnrichmentPrompt.user(tender));
        body.put("format", EnrichmentPrompt.schema(wantTitle, wantRequirements));
        body.put("keep_alive", props.getKeepAlive());
        body.put("options", options);

        long started = System.currentTimeMillis();
        String raw = call(http, body, props.getModel());
        log.debug("enriched tender {} in {} ms", tender.getExternalId(), System.currentTimeMillis() - started);
        return parse(raw);
    }

    private String call(RestClient client, Map<String, Object> body, String model) {
        try {
            return client.post().uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new LlmUnavailableException("model " + model
                    + " is not installed -- run: ollama pull " + model, e);
        } catch (ResourceAccessException e) {
            if (connectFailure(e)) {
                throw new LlmUnavailableException("Ollama is not reachable at " + props.getBaseUrl(), e);
            }
            throw new LlmCallException("model call did not finish: " + e.getMessage(), e);
        } catch (RestClientResponseException e) {
            throw new LlmCallException("Ollama answered " + e.getStatusCode() + ": "
                    + e.getResponseBodyAsString(), e);
        }
    }

    TenderEnrichment parse(String raw) {
        JsonNode r = response(raw);
        return new TenderEnrichment(
                text(r, "short_title"),
                text(r, "summary"),
                list(r, "deliverables"),
                text(r, "location"),
                money(r, "min_turnover_bdt"),
                integer(r, "min_experience_years"),
                list(r, "certifications"));
    }

    /** Ollama wraps the model's JSON in an envelope; this unwraps and checks both. */
    private JsonNode response(String raw) {
        JsonNode r;
        try {
            JsonNode envelope = json.readTree(raw == null ? "" : raw);
            r = json.readTree(envelope.path("response").asString());
        } catch (JacksonException e) {
            throw new LlmCallException("reply is not the JSON asked for", e);
        }
        if (r == null || !r.isObject()) {
            throw new LlmCallException("reply is not a JSON object");
        }
        return r;
    }

    private static String text(JsonNode r, String field) {
        JsonNode n = r.get(field);
        return n == null || n.isNull() ? null : n.asString();
    }

    private static List<String> list(JsonNode r, String field) {
        JsonNode n = r.get(field);
        List<String> out = new ArrayList<>();
        if (n != null && n.isArray()) {
            for (JsonNode item : n) {
                if (!item.isNull()) {
                    out.add(item.asString());
                }
            }
        }
        return out;
    }

    private static BigDecimal money(JsonNode r, String field) {
        JsonNode n = r.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        return n.isNumber() ? n.decimalValue() : MoneyTextParser.largest(n.asString());
    }

    private static Integer integer(JsonNode r, String field) {
        JsonNode n = r.get(field);
        return n == null || n.isNull() || !n.isNumber() ? null : n.intValue();
    }

    private static boolean connectFailure(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof ConnectException || c instanceof HttpConnectTimeoutException
                    || c instanceof UnresolvedAddressException) {
                return true;
            }
        }
        return false;
    }
}
