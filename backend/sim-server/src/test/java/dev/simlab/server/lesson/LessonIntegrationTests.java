package dev.simlab.server.lesson;

import dev.simlab.core.SimulationDefinition;
import dev.simlab.server.simulation.SimulationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Lessons of a running server, fetched like the frontend does */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class LessonIntegrationTests {

    private final RestTestClient client;
    private final SimulationService simulations;

    LessonIntegrationTests(@Autowired RestTestClient client, @Autowired SimulationService simulations) {
        this.client = client;
        this.simulations = simulations;
    }

    @Test
    void servesALessonWithCodeForEverySimulation() {
        for (SimulationDefinition definition : simulations.all()) {
            client.get()
                    .uri("/api/simulations/{type}/lesson", definition.type())
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.blocks[?(@.kind == 'Code')]")
                    .isNotEmpty();
        }
    }
}
