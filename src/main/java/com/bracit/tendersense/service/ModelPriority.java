package com.bracit.tendersense.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Who gets the model first.
 *
 * <p>One Ollama serves both jobs: the batch worker reading newly fetched tenders, and a
 * comparison someone is waiting for on a tender page. Measured on this machine, a call
 * spends ~12s generating and the rest of its minute queued behind the batch -- so the
 * batch stands aside while anyone is waiting, and picks up again the moment they are not.
 *
 * <p>Nobody waits on the batch: it runs every 30 seconds and the tenders are still there
 * next time. Someone waiting on a page is watching a spinner.
 */
@Component
@Slf4j
public class ModelPriority {

    private final AtomicInteger waiting = new AtomicInteger();

    /** Marks the start of a call someone is waiting for. Always paired with {@link #done()}. */
    public void started() {
        int n = waiting.incrementAndGet();
        if (n == 1) {
            log.debug("a reader is waiting on the model: batch processing stands down");
        }
    }

    public void done() {
        if (waiting.decrementAndGet() <= 0) {
            waiting.set(0);
            log.debug("nobody waiting: batch processing resumes");
        }
    }

    /** True while someone is waiting on a page for the model. */
    public boolean someoneIsWaiting() {
        return waiting.get() > 0;
    }
}
