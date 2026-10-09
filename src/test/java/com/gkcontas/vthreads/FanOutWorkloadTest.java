package com.gkcontas.vthreads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gkcontas.vthreads.workload.FanOutWorkload;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Structured concurrency, asserted on the two properties that make it worth a
 * preview-API dependency: the legs really run in parallel, and a failure
 * cancels the siblings instead of waiting for them.
 */
class FanOutWorkloadTest {

    private final FanOutWorkload workload = new FanOutWorkload();

    @Test
    @DisplayName("eight legs of 100ms take about 100ms, not 800ms")
    void legsRunInParallel() throws Exception {
        var result = workload.gather(8, Duration.ofMillis(100), Duration.ofSeconds(5));

        assertThat(result.results()).hasSize(8).contains("leg-0", "leg-7");
        // Generous upper bound: the point is the order of magnitude, not a
        // tight timing assertion that would be flaky on a loaded machine.
        assertThat(result.elapsedMillis()).isLessThan(400L);
    }

    @Test
    @DisplayName("a failing leg cancels the siblings instead of waiting for them")
    void failureShortCircuits() {
        long startedAt = System.nanoTime();

        assertThatThrownBy(() -> workload.gatherWithFailure(
                4, Duration.ofSeconds(2), 0, Duration.ofSeconds(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("siblings were cancelled");

        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
        // The slow legs would have taken 2000ms. Returning well inside that is
        // the only observable proof that they were cancelled.
        assertThat(elapsedMillis).isLessThan(1_000L);
    }

    @Test
    @DisplayName("a deadline applies to the whole fan-out, not to each leg")
    void deadlineCoversTheWholeScope() {
        assertThatThrownBy(() -> workload.gather(
                4, Duration.ofSeconds(5), Duration.ofMillis(200)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not finish within");
    }
}
