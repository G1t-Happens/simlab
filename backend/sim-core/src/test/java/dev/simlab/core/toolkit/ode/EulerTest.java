package dev.simlab.core.toolkit.ode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EulerTest {

    /** harmonic oscillator {@code x'' = -x} as a system of x and v, solved by x = cos t, v = -sin t */
    private static final OdeSystem OSCILLATOR = (t, y, dydt) -> {
        dydt[0] = y[1];
        dydt[1] = -y[0];
    };

    private static final double END_TIME = 2;

    @ParameterizedTest
    @ValueSource(doubles = {0.01, 0.005, 0.0025})
    void halvingTheStepSizeHalvesTheError(double stepSize) {
        double ratio = globalError(stepSize) / globalError(stepSize / 2);

        assertThat(ratio).isCloseTo(2, within(0.02));
    }

    @Test
    void multipliesTheEnergyOfAnOscillatorByOnePlusTheSquaredStepInEveryStep() {
        var integrator = new Euler(2);
        double[] y = {0.6, 0.8};
        double step = 0.1;

        integrator.step(OSCILLATOR, 0, y, step);

        // the exact solution keeps x² + v² = 1, Euler's method lets it grow without bound
        assertThat(y[0] * y[0] + y[1] * y[1]).isCloseTo(1 + step * step, within(1e-12));
    }

    @Test
    void rejectsStatesOfAnotherDimension() {
        var integrator = new Euler(2);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> integrator.step(OSCILLATOR, 0, new double[3], 0.1))
                .withMessage("Expected a state of dimension 2, but got 3");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsDimensionsBelowOne(int dimension) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Euler(dimension));
    }

    /** integrates the oscillator from 0 to {@link #END_TIME}, returns the distance to the exact solution */
    private static double globalError(double stepSize) {
        var integrator = new Euler(2);
        double[] y = {1, 0};
        long steps = Math.round(END_TIME / stepSize);
        for (long i = 0; i < steps; i++) {
            integrator.step(OSCILLATOR, i * stepSize, y, stepSize);
        }
        return Math.hypot(y[0] - Math.cos(END_TIME), y[1] + Math.sin(END_TIME));
    }
}
