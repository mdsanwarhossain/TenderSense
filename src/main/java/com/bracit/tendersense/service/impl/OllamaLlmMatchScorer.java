package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.LlmProperties;
import com.bracit.tendersense.dto.LlmMatchVerdict;
import com.bracit.tendersense.exception.LlmScoringException;
import com.bracit.tendersense.exception.LlmUnavailableException;
import com.bracit.tendersense.service.LlmMatchScorer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Scores one tender with a local Ollama model through Spring AI's {@link ChatClient}.
 *
 * <p>Structured output is enforced twice over. The JSON schema that
 * {@link BeanOutputConverter} derives from {@link LlmMatchVerdict} goes to Ollama as its
 * {@code format}, so decoding is constrained to the schema; the reply is then converted and
 * range-checked here. The first catches malformed JSON, the second catches valid JSON with
 * nonsense in it -- a score of 140, an empty reason.
 *
 * <p>Deliberately {@code content()} + {@code convert()} rather than {@code entity()}: a
 * rejected reply has to be shown back to the model on the retry, and {@code entity()}
 * throws away the raw text it failed on.
 */
@Service
@Slf4j
public class OllamaLlmMatchScorer implements LlmMatchScorer {

    private static final int MAX_REASONING = 400;
    /** A read timeout is transport, not content, so an identical retry is worth one go. */
    private static final int TIMEOUT_ATTEMPTS = 2;

    private final ChatClient chatClient;
    private final LlmProperties properties;
    private final String baseUrl;
    private final BeanOutputConverter<LlmMatchVerdict> converter =
            new BeanOutputConverter<>(LlmMatchVerdict.class);

    public OllamaLlmMatchScorer(ChatClient.Builder chatClientBuilder,
                                LlmProperties properties,
                                @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl) {
        this.chatClient = chatClientBuilder.build();
        this.properties = properties;
        this.baseUrl = baseUrl;
    }

    @Override
    public String modelVersion() {
        return "ollama:" + properties.getModel();
    }

    @Override
    public LlmMatchVerdict score(String systemPrompt, String tenderPrompt) {
        // The schema instructions never change, so they belong in the system prompt: that
        // keeps them inside the prefix Ollama reuses between tenders of one company.
        String system = systemPrompt + "\n\n" + converter.getFormat();
        List<Message> conversation = new ArrayList<>(List.of(new UserMessage(tenderPrompt)));

        int attempts = Math.max(1, properties.getMaxAttempts());
        String problem = "no reply";
        for (int attempt = 1; attempt <= attempts; attempt++) {
            String raw = call(system, conversation);
            try {
                return validated(converter.convert(raw));
            } catch (RuntimeException e) {
                problem = describe(e);
                log.debug("verdict attempt {}/{} rejected: {} -- reply: {}",
                        attempt, attempts, problem, abbreviate(raw, 300));
                // At temperature 0 an identical request returns the identical bad reply, so
                // a retry has to change the request: show the model its answer and say why.
                conversation.add(new AssistantMessage(raw == null ? "" : raw));
                conversation.add(new UserMessage("That reply was not valid: " + problem
                        + ". Reply again with only the JSON object, following the schema exactly."));
            }
        }
        throw new LlmScoringException(
                "no valid verdict after " + attempts + " attempts: " + problem);
    }

    private String call(String system, List<Message> conversation) {
        OllamaChatOptions.Builder options = OllamaChatOptions.builder();
        // One statement each: model() and temperature() are declared on a generic parent
        // builder and return its erased type, so a single fluent chain does not compile.
        options.model(properties.getModel());
        options.temperature(0.0);
        options.seed(properties.getSeed());
        // Without this, Ollama's small default context truncates an overflowing prompt from
        // the front -- which is exactly where the company profile sits.
        options.numCtx(properties.getNumCtx());
        options.format(converter.getJsonSchemaMap());

        RuntimeException lastTimeout = null;
        for (int attempt = 1; attempt <= TIMEOUT_ATTEMPTS; attempt++) {
            try {
                return chatClient.prompt()
                        .system(system)
                        .messages(conversation)
                        .options(options)
                        .call()
                        .content();
            } catch (RuntimeException e) {
                if (isModelMissing(e)) {
                    throw new LlmUnavailableException("model " + properties.getModel()
                            + " is not pulled -- run: ollama pull " + properties.getModel(), e);
                }
                if (isUnreachable(e)) {
                    throw new LlmUnavailableException("Ollama is unreachable at " + baseUrl, e);
                }
                if (!isReadTimeout(e)) {
                    throw new LlmScoringException("model call failed: " + rootMessage(e), e);
                }
                lastTimeout = e;
                log.warn("model call timed out (attempt {}/{})", attempt, TIMEOUT_ATTEMPTS);
            }
        }
        throw new LlmScoringException("model call timed out " + TIMEOUT_ATTEMPTS + " times", lastTimeout);
    }

    private static LlmMatchVerdict validated(LlmMatchVerdict verdict) {
        if (verdict == null) {
            throw new IllegalArgumentException("the reply held no verdict");
        }
        if (verdict.matchScore() < 0 || verdict.matchScore() > 100) {
            throw new IllegalArgumentException(
                    "match_score " + verdict.matchScore() + " is outside 0-100");
        }
        String reasoning = verdict.reasoning() == null ? "" : verdict.reasoning().strip();
        if (reasoning.isEmpty()) {
            throw new IllegalArgumentException("reasoning is empty");
        }
        return new LlmMatchVerdict(verdict.matchScore(), abbreviate(reasoning, MAX_REASONING));
    }

    // ---- failure classification: each kind is handled differently by the review job ----

    private static boolean isUnreachable(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConnectException || t instanceof UnknownHostException
                    || t instanceof NoRouteToHostException || t instanceof HttpConnectTimeoutException) {
                return true;
            }
            if (t instanceof SocketTimeoutException && lower(t).contains("connect")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isReadTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpConnectTimeoutException) {
                return false;
            }
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /** Ollama answers a missing model with 404 "model ... not found, try pulling it first". */
    private static boolean isModelMissing(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpClientErrorException.NotFound) {
                return true;
            }
            String m = lower(t);
            if (m.contains("not found") && (m.contains("pull") || m.contains("model"))) {
                return true;
            }
        }
        return false;
    }

    private static String describe(Throwable e) {
        return abbreviate(rootMessage(e), 200);
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String m = root.getMessage();
        return m == null || m.isBlank() ? root.getClass().getSimpleName() : m.strip();
    }

    private static String lower(Throwable t) {
        return t.getMessage() == null ? "" : t.getMessage().toLowerCase(Locale.ROOT);
    }

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return null;
        }
        String flat = s.replaceAll("\\s+", " ").strip();
        return flat.length() <= max ? flat : flat.substring(0, max - 1).stripTrailing() + "…";
    }
}
