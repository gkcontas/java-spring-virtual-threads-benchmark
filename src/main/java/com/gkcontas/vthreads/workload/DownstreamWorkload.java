package com.gkcontas.vthreads.workload;

import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * A slow remote call, standing in for the third-party API or internal service
 * that most request latency actually goes to.
 * <p>
 * It is a sleep rather than a real HTTP call on purpose, and the distinction is
 * worth being precise about: what matters to the thread model is only that the
 * thread is <em>parked and holding nothing</em> for a known duration. A real
 * HTTP client would add its own connection pool, which is the one thing this
 * workload is meant to be free of - {@link DbWorkload} already covers what
 * happens when a pool is the ceiling.
 * <p>
 * This is the workload where virtual threads can actually win, because nothing
 * is scarce except threads.
 */
@Component
public class DownstreamWorkload {

    public long call(Duration delay) {
        try {
            Thread.sleep(delay);
            return delay.toMillis();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting on the downstream", e);
        }
    }
}
