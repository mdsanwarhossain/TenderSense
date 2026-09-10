package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.LlmProperties;
import com.bracit.tendersense.dto.LlmMatchVerdict;
import com.bracit.tendersense.exception.LlmScoringException;
import com.bracit.tendersense.exception.LlmUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.web.client.ResourceAccessException;
import reactor.core.publisher.Flux;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The scorer against a scripted model: no Ollama needed. Each test queues the replies
 * (or failures) the "model" will produce and asserts what the scorer did with them.
 */
class OllamaLlmMatchScorerTest {

    /** Plays back queued replies and records every prompt it was sent. */
    static final class ScriptedModel implements ChatModel {
        final Deque<Object> script = new ArrayDeque<>();
        final List<Prompt> prompts = new ArrayList<>();

        ScriptedModel then(Object replyOrFailure) {
            script.add(replyOrFailure);
            return this;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            Object next = script.removeFirst();
            if (next instanceof RuntimeException e) {
                throw e;
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage((String) next))));
        }

        /**
         * Mirrors OllamaChatModel. ChatClient builds each prompt's options by taking
         * {@code chatModel.getOptions()}, calling mutate(), then combineWith(request options)
         * -- so the model's own options type decides which fields survive. A stub left on the
         * generic default would receive DefaultChatOptions and every Ollama-only field
         * (num_ctx, format, seed) would be dropped, which is exactly what this test guards.
         */
        @Override
        public ChatOptions getOptions() {
            return OllamaChatOptions.builder().build();
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            throw new UnsupportedOperationException("not streamed");
        }
    }

    private static final String VALID = "{\"match_score\": 82, \"reasoning\": \"ERP rollout matches a core service.\"}";

    private static OllamaLlmMatchScorer scorer(ScriptedModel model) {
        LlmProperties props = new LlmProperties();
        props.setMaxAttempts(2);
        return new OllamaLlmMatchScorer(ChatClient.builder(model), props, "http://ollama.test:11434");
    }

    @Test
    @DisplayName("a valid reply becomes a typed verdict, and the options really reach the model")
    void validReply() {
        ScriptedModel model = new ScriptedModel().then(VALID);

        LlmMatchVerdict v = scorer(model).score("SYSTEM", "TENDER");

        assertEquals(82, v.matchScore());
        assertEquals("ERP rollout matches a core service.", v.reasoning());
        assertEquals(1, model.prompts.size());

        // If ChatClient dropped these on the way, Ollama would run at its defaults: random
        // sampling, a small context that truncates the profile, and no schema constraint.
        OllamaChatOptions options = assertInstanceOf(OllamaChatOptions.class, model.prompts.get(0).getOptions());
        assertEquals(0.0, options.getTemperature());
        assertEquals(8192, options.getNumCtx());
        assertNotNull(options.getFormat(), "the JSON schema must be sent as Ollama's format");
    }

    @Test
    @DisplayName("a malformed reply is retried with the bad answer shown back -- not an identical request")
    void malformedThenValid() {
        ScriptedModel model = new ScriptedModel().then("Sure! The score is 82.").then(VALID);

        LlmMatchVerdict v = scorer(model).score("SYSTEM", "TENDER");

        assertEquals(82, v.matchScore());
        assertEquals(2, model.prompts.size());
        List<Message> retry = model.prompts.get(1).getInstructions();
        assertTrue(retry.stream().anyMatch(m -> m instanceof AssistantMessage
                        && m.getText().contains("Sure! The score is 82.")),
                "the retry must carry the rejected reply, or temperature 0 just repeats it");
        assertTrue(retry.stream().anyMatch(m -> m.getText() != null && m.getText().contains("was not valid")));
    }

    @Test
    @DisplayName("valid JSON with an impossible score is rejected too")
    void outOfRangeThenValid() {
        ScriptedModel model = new ScriptedModel()
                .then("{\"match_score\": 140, \"reasoning\": \"Very good.\"}").then(VALID);

        assertEquals(82, scorer(model).score("SYSTEM", "TENDER").matchScore());
        assertEquals(2, model.prompts.size());
    }

    @Test
    @DisplayName("no valid reply after every attempt is a per-tender failure")
    void givesUp() {
        ScriptedModel model = new ScriptedModel().then("nope").then("{\"match_score\": 50}");

        LlmScoringException e = assertThrows(LlmScoringException.class,
                () -> scorer(model).score("SYSTEM", "TENDER"));
        assertTrue(e.getMessage().contains("after 2 attempts"));
    }

    @Test
    @DisplayName("an unreachable server aborts at once, so the run does not wait out 25 timeouts")
    void unreachable() {
        ScriptedModel model = new ScriptedModel()
                .then(new ResourceAccessException("I/O error", new ConnectException("Connection refused")));

        LlmUnavailableException e = assertThrows(LlmUnavailableException.class,
                () -> scorer(model).score("SYSTEM", "TENDER"));
        assertTrue(e.getMessage().contains("http://ollama.test:11434"));
        assertEquals(1, model.prompts.size());
    }

    @Test
    @DisplayName("a model that was never pulled says how to fix it")
    void modelMissing() {
        ScriptedModel model = new ScriptedModel().then(new RuntimeException(
                "404 - {\"error\":\"model \\\"qwen2.5:7b\\\" not found, try pulling it first\"}"));

        LlmUnavailableException e = assertThrows(LlmUnavailableException.class,
                () -> scorer(model).score("SYSTEM", "TENDER"));
        assertTrue(e.getMessage().contains("ollama pull qwen2.5:7b"));
    }

    @Test
    @DisplayName("a read timeout is transport, not content, so one identical retry is allowed")
    void timeoutThenValid() {
        ScriptedModel model = new ScriptedModel()
                .then(new ResourceAccessException("I/O error", new HttpTimeoutException("request timed out")))
                .then(VALID);

        assertEquals(82, scorer(model).score("SYSTEM", "TENDER").matchScore());
        assertEquals(2, model.prompts.size());
    }
}
