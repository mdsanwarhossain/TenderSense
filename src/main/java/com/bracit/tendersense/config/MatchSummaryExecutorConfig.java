package com.bracit.tendersense.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * The threads that write tender comparisons while readers wait on the page.
 *
 * <p>Two of them, matching {@code OLLAMA_NUM_PARALLEL}: more would only queue inside
 * Ollama, where we can no longer tell a reader that nothing is happening. The queue is
 * short and overflow is rejected rather than piled up -- a page whose turn never comes
 * polls again, and a request from ten minutes ago is nobody's question any more.
 */
@Configuration
public class MatchSummaryExecutorConfig {

    @Bean
    public Executor matchSummaryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("match-summary-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
