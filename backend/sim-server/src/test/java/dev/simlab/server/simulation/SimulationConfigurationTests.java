package dev.simlab.server.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.simlab.core.SimulationDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SimulationConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SimulationConfiguration.class, SimulationService.class);

    @Test
    void offersEverySimulationOfSimCoreWithoutRegisteringIt() {
        contextRunner.run(
                context -> assertThat(context.getBean(SimulationService.class).all())
                        .extracting(SimulationDefinition::type)
                        .contains("cloth", "galaxies", "lorenz"));
    }
}
