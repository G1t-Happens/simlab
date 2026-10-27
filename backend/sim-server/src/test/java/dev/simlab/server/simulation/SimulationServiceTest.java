package dev.simlab.server.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.simlab.core.simulations.cloth.ClothDefinition;
import dev.simlab.core.simulations.lorenz.LorenzDefinition;
import dev.simlab.server.FakeDefinition;
import dev.simlab.server.FakeSimulation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SimulationServiceTest {

    private final LorenzDefinition lorenz = new LorenzDefinition();
    private final ClothDefinition cloth = new ClothDefinition();
    private final SimulationService simulations = new SimulationService(List.of(lorenz, cloth));

    @Test
    void listsEverySimulationInTheOrderOfTheirTypes() {
        assertThat(simulations.all()).containsExactly(cloth, lorenz);
    }

    @Test
    void findsSimulationsByTheirType() {
        assertThat(simulations.find("lorenz")).contains(lorenz);
        assertThat(simulations.find("cloth")).contains(cloth);
        assertThat(simulations.find("quantum")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"n-body", "fluid2d", "a-b-c"})
    void acceptsTypesOfLowerCaseWordsWithHyphensBetweenThem(String type) {
        var definition = new FakeDefinition(type, new FakeSimulation(), 1);

        assertThat(new SimulationService(List.of(definition)).find(type)).contains(definition);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Cloth", "n body", "n--body", "-cloth", "cloth-", "../cloth"})
    void rejectsMalformedTypes(String type) {
        var definition = new FakeDefinition(type, new FakeSimulation(), 1);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SimulationService(List.of(definition)))
                .withMessageStartingWith(
                        "Type '%s' of %s must be lower-case letters and digits", type, FakeDefinition.class.getName());
    }

    @Test
    void rejectsSimulationsOfTheSameType() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new SimulationService(List.of(cloth, new FakeDefinition("cloth", new FakeSimulation(), 1))))
                .withMessage("There is more than one simulation of type 'cloth'");
    }
}
