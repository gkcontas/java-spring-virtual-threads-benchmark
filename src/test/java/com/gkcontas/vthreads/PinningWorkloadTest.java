package com.gkcontas.vthreads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.gkcontas.vthreads.workload.PinningWorkload;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import jdk.jfr.consumer.RecordingStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The central claim of this project, asserted rather than described: on Java 21
 * a virtual thread that blocks inside {@code synchronized} pins its carrier,
 * and the same code under a {@link java.util.concurrent.locks.ReentrantLock}
 * does not.
 * <p>
 * No Spring, no database - the subject is the JDK's scheduler. The evidence is
 * the JFR event {@code jdk.VirtualThreadPinned}, the same source the running
 * application reports from.
 */
class PinningWorkloadTest {

    private static final String EVENT = "jdk.VirtualThreadPinned";
    private static final int TASKS = 16;
    private static final Duration BLOCK = Duration.ofMillis(50);

    @Test
    @DisplayName("blocking inside synchronized pins the carrier, once per task")
    void synchronizedPins() throws Exception {
        long pinned = countPinningEvents(workload -> workload.insideMonitor(BLOCK));

        // One event per task: every one of them blocked while holding a monitor.
        assertThat(pinned).isEqualTo(TASKS);
    }

    @Test
    @DisplayName("blocking inside a ReentrantLock does not pin")
    void reentrantLockDoesNotPin() throws Exception {
        long pinned = countPinningEvents(workload -> workload.insideLock(BLOCK));

        assertThat(pinned).isZero();
    }

    @Test
    @DisplayName("both guards do the same work and return the same thing")
    void bothGuardsAreEquivalent() {
        PinningWorkload workload = new PinningWorkload();

        assertThat(workload.insideMonitor(BLOCK)).isEqualTo(BLOCK.toMillis());
        assertThat(workload.insideLock(BLOCK)).isEqualTo(BLOCK.toMillis());
    }

    /** True when this pinning event happened inside the workload under test. */
    private static boolean causedByWorkload(jdk.jfr.consumer.RecordedEvent event) {
        if (event.getStackTrace() == null) {
            return false;
        }
        return event.getStackTrace().getFrames().stream()
                .anyMatch(frame -> frame.getMethod().getType().getName()
                        .equals(PinningWorkload.class.getName()));
    }

    /**
     * Runs {@code body} on {@value #TASKS} virtual threads and returns how many
     * pinning events the JDK reported from inside the workload.
     */
    private long countPinningEvents(java.util.function.Consumer<PinningWorkload> body)
            throws Exception {
        PinningWorkload workload = new PinningWorkload();
        AtomicLong pinned = new AtomicLong();

        try (var recording = new RecordingStream()) {
            // Without withoutThreshold() the event only fires for pinning long
            // enough to be considered interesting, and a test wants every one.
            recording.enable(EVENT).withoutThreshold().withStackTrace();
            // Counting every pinning event in the JVM made this flaky: a test
            // run saw 17 where 16 tasks had run, because other code in the test
            // JVM pins too. Filtering by stack trace makes the count exact and
            // lets the ReentrantLock case assert a hard zero.
            recording.onEvent(EVENT, event -> {
                if (causedByWorkload(event)) {
                    pinned.incrementAndGet();
                }
            });
            recording.startAsync();

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int task = 0; task < TASKS; task++) {
                    executor.submit(() -> body.accept(workload));
                }
            }

            // JFR delivers asynchronously, so the count has to be waited for
            // rather than read. For the zero case this simply expires, which is
            // the only way to assert that nothing happened.
            try {
                await().atMost(Duration.ofSeconds(5))
                        .until(() -> pinned.get() >= TASKS);
            } catch (org.awaitility.core.ConditionTimeoutException expectedWhenNotPinning) {
                // Nothing to do: the assertion on the returned count decides.
            }
            return pinned.get();
        }
    }
}
