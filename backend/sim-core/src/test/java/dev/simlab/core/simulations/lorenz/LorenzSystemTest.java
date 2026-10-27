package dev.simlab.core.simulations.lorenz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.simlab.core.InvalidInputException;
import org.junit.jupiter.api.Test;

class LorenzSystemTest {

    private final LorenzSystem system = new LorenzSystem(10, 28, 2);

    @Test
    void evaluatesTheLorenzEquationsForEveryParticle() {
        double[] derivative = new double[6];

        system.derivative(0, new double[] {1, 2, 3, -1, 0, 5}, derivative);

        assertThat(derivative).containsExactly(10, 23, -4, 10, -23, -10);
    }

    @Test
    void appliesChangedParameters() {
        double[] derivative = new double[3];
        system.setParameter(LorenzDefinition.SIGMA, 1);
        system.setParameter(LorenzDefinition.RHO, 5);
        system.setParameter(LorenzDefinition.BETA, 0.5);

        system.derivative(0, new double[] {1, 2, 3}, derivative);

        assertThat(derivative).containsExactly(1, 0, 0.5);
    }

    @Test
    void rejectsUnknownParameters() {
        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> system.setParameter("gamma", 1));
    }
}
