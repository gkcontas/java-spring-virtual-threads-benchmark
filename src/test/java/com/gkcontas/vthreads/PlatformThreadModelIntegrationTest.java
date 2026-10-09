package com.gkcontas.vthreads;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

/** The other half of {@link ThreadModelIntegrationTest}: the default is the platform pool. */
class PlatformThreadModelIntegrationTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("without the virtual profile, requests are served on platform threads")
    void requestsRunOnPlatformThreads() {
        String body = rest.getForObject("/ping", String.class);

        assertThat(body).contains("\"virtual\":false").contains("http-nio-");
    }
}
