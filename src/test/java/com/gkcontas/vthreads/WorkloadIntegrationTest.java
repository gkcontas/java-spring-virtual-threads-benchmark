package com.gkcontas.vthreads;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.vthreads.bench.PinningMonitor;
import com.gkcontas.vthreads.bench.RuntimeInspector;
import com.gkcontas.vthreads.workload.DbWorkload;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

/**
 * The endpoints the simulations drive, against a real database. Keeps the load
 * runs honest: if these pass, a bad number from Gatling is about the thread
 * model, not about a broken endpoint.
 */
class WorkloadIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private DbWorkload db;

    @Autowired
    private RuntimeInspector inspector;

    @Autowired
    private PinningMonitor pinning;

    @Test
    @DisplayName("the seeded catalog is there and the delay is respected")
    void databaseWorkloadBlocksForTheRequestedTime() {
        long startedAt = System.nanoTime();
        long inStock = db.query(Duration.ofMillis(300));
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        // 20000 rows are seeded; how many are in stock is random, so only the
        // bounds can be asserted.
        assertThat(inStock).isPositive().isLessThanOrEqualTo(20_000);
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(300L);
    }

    @Test
    @DisplayName("every workload endpoint answers")
    void endpointsAnswer() {
        assertThat(rest.getForEntity("/work/downstream?delayMs=10", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/work/db?delayMs=10", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/work/cpu?rounds=100", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/work/pinned?delayMs=10", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/work/unpinned?delayMs=10", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/work/fanout?legs=3&delayMs=20", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("an out-of-range parameter is refused, so a simulation cannot ask for nonsense")
    void parametersAreValidated() {
        assertThat(rest.getForEntity("/work/downstream?delayMs=999999", String.class)
                .getStatusCode().is4xxClientError()).isTrue();
        assertThat(rest.getForEntity("/work/fanout?legs=999", String.class)
                .getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @DisplayName("the runtime report describes the configuration the numbers came from")
    void runtimeReportIsPopulated() {
        var report = inspector.report();

        assertThat(report.availableProcessors()).isPositive();
        assertThat(report.carriers()).isPositive();
        assertThat(report.platformThreadCount()).isPositive();
        assertThat(report.connectionPool()).containsKey("max");
        assertThat(report.heapUsedMb()).isPositive();
    }

    @Test
    @DisplayName("the pinning monitor is running, and says so explicitly")
    void pinningMonitorIsAvailable() {
        // Reported as a flag rather than inferred from a zero count: "nothing
        // pinned" and "nothing was watching" must not look the same.
        assertThat(pinning.report().monitoringAvailable()).isTrue();
    }

    @Test
    @DisplayName("the fan-out endpoint reports the short-circuit instead of hanging")
    void fanoutFailureIsReported() {
        var response = rest.getForEntity(
                "/work/fanout-failure?legs=3&delayMs=500&failingLeg=0", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("short-circuited");
    }
}
