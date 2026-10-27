package dev.simlab.core.toolkit.ode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RungeKutta4Test {

    /** harmonic oscillator {@code x'' = -x} as a system of x and v, solved by x = cos t, v = -sin t */
    private static final OdeSystem OSCILLATOR = (t, y, dydt) -> {
        dydt[0] = y[1];
        dydt[1] = -y[0];
    };

    private static final double END_TIME = 2;

    @ParameterizedTest
    @ValueSource(doubles = {0.1, 0.05, 0.025})
    void halvingTheStepSizeReducesTheErrorToOneSixteenth(double stepSize) {
        double ratio = globalError(stepSize) / globalError(stepSize / 2);

        assertThat(ratio).isCloseTo(16, within(0.25));
    }

    @Test
    void rejectsStatesOfAnotherDimension() {
        var integrator = new RungeKutta4(2);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> integrator.step(OSCILLATOR, 0, new double[3], 0.1))
                .withMessage("Expected a state of dimension 2, but got 3");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsDimensionsBelowOne(int dimension) {
        assertThatIllegalArgumentException().isThrownBy(() -> new RungeKutta4(dimension));
    }

    /** integrates the oscillator from 0 to {@link #END_TIME}, returns the distance to the exact solution */
    private static double globalError(double stepSize) {
        var integrator = new RungeKutta4(2);
        double[] y = {1, 0};
        long steps = Math.round(END_TIME / stepSize);
        for (long i = 0; i < steps; i++) {
            integrator.step(OSCILLATOR, i * stepSize, y, stepSize);
        }
        return Math.hypot(y[0] - Math.cos(END_TIME), y[1] + Math.sin(END_TIME));
    }
}
