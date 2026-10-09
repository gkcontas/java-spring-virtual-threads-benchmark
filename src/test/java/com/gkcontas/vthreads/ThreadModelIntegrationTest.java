package com.gkcontas.vthreads;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves the switch under test actually switches something.
 * <p>
 * Every number in the README depends on this: a benchmark that compared two
 * configurations which were secretly the same would produce beautifully
 * consistent and completely worthless results.
 */
@ActiveProfiles("virtual")
class ThreadModelIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("with the virtual profile, requests are served on virtual threads")
    void requestsRunOnVirtualThreads() {
        String body = rest.getForObject("/ping", String.class);

        assertThat(body).contains("\"virtual\":true").contains("VirtualThread");
    }

    @Test
    @DisplayName("the runtime report agrees that virtual threads are on")
    void runtimeReportAgrees() {
        String body = rest.getForObject("/bench/runtime", String.class);

        assertThat(body).contains("\"virtualThreadsEnabled\":true");
    }
}
