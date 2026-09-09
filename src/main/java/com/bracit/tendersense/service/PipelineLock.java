package com.bracit.tendersense.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Serialises pipeline work so two runs can never overlap.
 *
 * <p>This is not hypothetical: two concurrent rescores raced and collided on the
 * eligibility_verdict unique key, because each checked "does a verdict exist?" before
 * the other had committed. A nightly reconcile overlapping a 30-minute discovery
 * sweep would do the same thing.
 *
 * <p>An in-process lock is sufficient <em>only</em> because TenderSense runs as a
 * single instance. Running two replicas would reintroduce the race and also double
 * the crawl load on a government portal, so scaling out needs a database lock first.
 */
@Component
@Slf4j
public class PipelineLock {

    private final AtomicReference<String> holder = new AtomicReference<>();

    public boolean isBusy() {
        return holder.get() != null;
    }

    public String currentHolder() {
        return holder.get();
    }

    /**
     * Runs {@code work} if no other pipeline job is active.
     *
     * @return the result, or empty when another job holds the lock
     */
    public <T> java.util.Optional<T> runExclusively(String jobName, Supplier<T> work) {
        if (!holder.compareAndSet(null, jobName)) {
            log.warn("skipping {} - {} is still running", jobName, holder.get());
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.ofNullable(work.get());
        } finally {
            holder.set(null);
        }
    }
}
