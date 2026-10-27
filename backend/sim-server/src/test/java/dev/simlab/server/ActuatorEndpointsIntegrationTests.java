package dev.simlab.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Actuator endpoints of a running server, as the frontend sees them */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ActuatorEndpointsIntegrationTests {

    private final RestTestClient client;

    ActuatorEndpointsIntegrationTests(@Autowired RestTestClient client) {
        this.client = client;
    }

    @Test
    void healthReportsUpWithoutExposingDetails() {
        client.get()
                .uri("/actuator/health")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP")
                .jsonPath("$.components")
                .doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"beans", "configprops", "env", "heapdump", "loggers", "mappings", "threaddump"})
    void sensitiveEndpointsAreNotExposed(String endpoint) {
        client.get()
                .uri("/actuator/{endpoint}", endpoint)
                .exchange()
                .expectStatus()
                .isNotFound();
    }
}
