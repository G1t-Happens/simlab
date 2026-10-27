package dev.simlab.core.simulations.lorenz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import dev.simlab.core.Deselect;
import dev.simlab.core.Grab;
import dev.simlab.core.InvalidInputException;
import dev.simlab.core.ParameterChange;
import dev.simlab.core.Select;
import java.nio.FloatBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LorenzSimulationTest {

    private static final double SIGMA = 10;
    private static final double RHO = 28;
    private static final double BETA = 8.0 / 3;

    /** the default step size */
    private static final double STEP = 0.005;

    /** x, y, z of the selected particle and their rates of change */
    private static final int READOUTS = 6;

    @Test
    void writesThreeCoordinatesPerParticleFollowedByTheReadouts() {
        assertThat(frame(simulation(2))).hasSize(6 + READOUTS);
    }

    @Test
    void startsTheParticlesCloseTogetherOnTheAttractor() {
        float[] positions = positions(simulation(1_000));

        assertThat(positions[2]).as("height of the first particle").isBetween(5f, 50f);
        for (int i = 3; i < positions.length; i += 3) {
            assertThat(positions[i]).isCloseTo(positions[0], within(0.1f));
            assertThat(positions[i + 1]).isEqualTo(positions[1]);
            assertThat(positions[i + 2]).isEqualTo(positions[2]);
        }
    }

    @Test
    void advancesTheSimulatedTimeInWholeStepsAndCarriesTheRestOver() {
        LorenzSimulation simulation = simulation(1);

        // two frames are 0.0533 units of time, four are 0.107
        simulation.step(1.0 / 30);
        simulation.step(1.0 / 30);
        double afterTwoFrames = simulation.time();
        simulation.step(1.0 / 30);
        simulation.step(1.0 / 30);

        assertThat(afterTwoFrames).as("ten steps").isCloseTo(10 * STEP, within(1e-12));
        assertThat(simulation.time()).as("21 steps").isCloseTo(21 * STEP, within(1e-12));
    }

    @Test
    void takesStepsLongerThanAFrameEveryFewFrames() {
        LorenzSimulation simulation = simulation(1);
        simulation.apply(new ParameterChange(LorenzDefinition.STEP, 0.1));

        // three frames are 0.08 units of time, four are 0.107
        for (int frame = 0; frame < 3; frame++) {
            simulation.step(1.0 / 30);
        }
        double afterThreeFrames = simulation.time();
        simulation.step(1.0 / 30);

        assertThat(afterThreeFrames).isZero();
        assertThat(simulation.time()).isCloseTo(0.1, within(1e-12));
    }

    @Test
    void followsTheRatesAtTheStartOfAStepForTheWholeStepWithEulersMethod() {
        LorenzSimulation simulation = simulation(1);
        simulation.apply(new ParameterChange(LorenzDefinition.ORDER, LorenzDefinition.EULER));
        simulation.apply(new Select(0));
        float[] start = readouts(simulation);

        // one and a half steps of time, one step of which passes
        simulation.step(1.5 * STEP / LorenzSimulation.TIME_SCALE);

        assertThat(Arrays.copyOf(readouts(simulation), 3))
                .containsExactly(
                        new float[] {
                            (float) (start[0] + STEP * start[3]),
                            (float) (start[1] + STEP * start[4]),
                            (float) (start[2] + STEP * start[5])
                        },
                        within(1e-4f));
    }

    @Test
    void movesTheParticlesOtherwiseWithEulersMethodThanWithTheRungeKuttaMethod() {
        LorenzSimulation euler = simulation(1);
        LorenzSimulation rungeKutta = simulation(1);
        euler.apply(new ParameterChange(LorenzDefinition.ORDER, LorenzDefinition.EULER));

        euler.step(1);
        rungeKutta.step(1);

        assertThat(positions(euler)[0]).isNotCloseTo(positions(rungeKutta)[0], within(1e-3f));
    }

    @ParameterizedTest
    @CsvSource({"4, 0.2", "1, 0.04"})
    void explodesWithStepsTooLongForTheMethod(double order, double step) {
        LorenzSimulation simulation = simulation(2);
        simulation.apply(new ParameterChange(LorenzDefinition.ORDER, order));
        simulation.apply(new ParameterChange(LorenzDefinition.STEP, step));

        simulation.step(100);

        for (float coordinate : positions(simulation)) {
            assertThat(Float.isFinite(coordinate)).isFalse();
        }
    }

    @Test
    void showsTheFlowAtThePointsOfAGridAsArrowsOfTheSameLengthWithTheSpeedAsTheirValue() {
        float[] xray = xray(simulation(1));

        assertThat(xray[0]).as("number of arrows").isEqualTo(LorenzSimulation.ARROW_COUNT);
        for (int arrow = 0; arrow < LorenzSimulation.ARROW_COUNT; arrow++) {
            int i = 1 + 7 * arrow;
            double x = xray[i];
            double y = xray[i + 1];
            double z = xray[i + 2];
            double[] rates = {SIGMA * (y - x), x * (RHO - z) - y, x * y - BETA * z};
            double speed = Math.sqrt(square(rates[0]) + square(rates[1]) + square(rates[2]));
            assertThat(xray[i + 6]).as("speed").isCloseTo((float) speed, within(1e-3f));
            for (int axis = 0; axis < 3; axis++) {
                assertThat(xray[i + 3 + axis] - xray[i + axis])
                        .as("arrow %d along axis %d", arrow, axis)
                        .isCloseTo((float) (LorenzSimulation.ARROW_LENGTH * rates[axis] / speed), within(1e-4f));
            }
        }
        assertThat(Arrays.copyOfRange(xray, 1, 4)).as("first point of the grid").containsExactly(-20, -20, 5);
    }

    @Test
    void showsTheFlowOfTheParametersOfTheMoment() {
        LorenzSimulation simulation = simulation(1);
        // the arrow at the point (-20, -15, 5), where σ drives x towards y
        int speed = 1 + 7 * LorenzSimulation.GRID_SIZE + 6;
        float before = xray(simulation)[speed];

        simulation.apply(new ParameterChange(LorenzDefinition.SIGMA, 2 * SIGMA));

        assertThat(xray(simulation)[speed]).isNotEqualTo(before);
    }

    @Test
    void rejectsMethodsOfOtherOrders() {
        LorenzSimulation simulation = simulation(1);

        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> simulation.apply(new ParameterChange(LorenzDefinition.ORDER, 2)))
                .withMessage("There is no method of order 2.0");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -0.005, Double.NaN})
    void rejectsStepSizesThatAreNotPositive(double step) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LorenzSimulation(1, SIGMA, RHO, BETA, LorenzDefinition.RUNGE_KUTTA_4, step));
    }

    @Test
    void letsNeighboringParticlesDriftApart() {
        LorenzSimulation simulation = simulation(2);

        simulation.step(60);

        float[] positions = positions(simulation);
        double distance = Math.sqrt(square(positions[0] - positions[3])
                + square(positions[1] - positions[4])
                + square(positions[2] - positions[5]));
        assertThat(distance).as("distance of particles that started 1e-4 apart").isGreaterThan(1);
        assertThat(largestMagnitude(positions))
                .as("largest coordinate on the attractor")
                .isLessThan(100);
    }

    @Test
    void bringsAllParticlesToRestAtTheOriginIfRhoIsBelowOne() {
        LorenzSimulation simulation = simulation(1_000);

        simulation.apply(new ParameterChange(LorenzDefinition.RHO, 0.5));
        simulation.step(40);

        assertThat(largestMagnitude(positions(simulation))).isLessThan(1e-3);
    }

    @Test
    void reportsNothingWhileNoParticleIsSelected() {
        assertThat(readouts(simulation(2))).containsOnly(Float.NaN);
    }

    @Test
    void reportsTheStateOfTheSelectedParticleAndTheRatesThatTheLorenzEquationsGive() {
        LorenzSimulation simulation = simulation(3);
        simulation.step(1);

        simulation.apply(new Select(1));

        float[] positions = positions(simulation);
        double x = positions[3];
        double y = positions[4];
        double z = positions[5];
        assertThat(readouts(simulation))
                .containsExactly(
                        new float[] {
                            (float) x,
                            (float) y,
                            (float) z,
                            (float) (SIGMA * (y - x)),
                            (float) (x * (RHO - z) - y),
                            (float) (x * y - BETA * z)
                        },
                        within(1e-3f));
    }

    @Test
    void reportsTheRatesForParametersThatChangedSinceTheLastStep() {
        LorenzSimulation simulation = simulation(1);
        simulation.step(1);
        simulation.apply(new Select(0));

        simulation.apply(new ParameterChange(LorenzDefinition.SIGMA, 2 * SIGMA));

        float[] readouts = readouts(simulation);
        assertThat(readouts[3]).isCloseTo((float) (2 * SIGMA * (readouts[1] - readouts[0])), within(1e-3f));
    }

    @Test
    void reportsNothingOnceTheParticleIsDeselected() {
        LorenzSimulation simulation = simulation(2);
        simulation.apply(new Select(1));

        simulation.apply(new Deselect());

        assertThat(readouts(simulation)).containsOnly(Float.NaN);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2})
    void rejectsSelectingAParticleThatDoesNotExist(int particle) {
        LorenzSimulation simulation = simulation(2);

        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> simulation.apply(new Select(particle)))
                .withMessage("There is no particle %d", particle);
    }

    @Test
    void rejectsNegativeTimeSpans() {
        LorenzSimulation simulation = simulation(1);

        assertThatIllegalArgumentException().isThrownBy(() -> simulation.step(-1));
    }

    @Test
    void rejectsGrabbingAParticle() {
        LorenzSimulation simulation = simulation(1);

        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> simulation.apply(new Grab(0, 1, 2, 3)));
    }

    private static LorenzSimulation simulation(int particleCount) {
        return new LorenzSimulation(particleCount, SIGMA, RHO, BETA, LorenzDefinition.RUNGE_KUTTA_4, STEP);
    }

    private static float[] positions(LorenzSimulation simulation) {
        float[] frame = frame(simulation);
        return Arrays.copyOf(frame, frame.length - READOUTS);
    }

    private static float[] readouts(LorenzSimulation simulation) {
        float[] frame = frame(simulation);
        return Arrays.copyOfRange(frame, frame.length - READOUTS, frame.length);
    }

    private static float[] frame(LorenzSimulation simulation) {
        FloatBuffer frame = FloatBuffer.allocate(3 * LorenzDefinition.PARTICLE_COUNT + READOUTS);
        simulation.writeFrame(frame);
        return Arrays.copyOf(frame.array(), frame.position());
    }

    private static float[] xray(LorenzSimulation simulation) {
        FloatBuffer xray = FloatBuffer.allocate(1 + 7 * LorenzSimulation.ARROW_COUNT);
        simulation.writeXray(xray);
        assertThat(xray.hasRemaining()).as("floats missing from the x-ray").isFalse();
        return xray.array();
    }

    private static double largestMagnitude(float[] coordinates) {
        double largest = 0;
        for (float coordinate : coordinates) {
            largest = Math.max(largest, Math.abs(coordinate));
        }
        return largest;
    }

    private static double square(double value) {
        return value * value;
    }
}
