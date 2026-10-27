package dev.simlab.core.simulations.galaxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

import dev.simlab.core.Deselect;
import dev.simlab.core.Grab;
import dev.simlab.core.InvalidInputException;
import dev.simlab.core.ParameterChange;
import dev.simlab.core.Select;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GalaxySimulationTest {

    /** enough disk stars to measure their shapes, little dark matter */
    private static final ParticleCounts COUNTS = new ParticleCounts(4_000, 400, 2_000);

    /** few particles that still move like galaxies, so a long collision takes little time */
    private static final ParticleCounts FEW = new ParticleCounts(500, 100, 500);

    private static final double PACE = 15;
    private static final double PERICENTER = 10;

    /** pairs, tree interactions, interactions and force error of the selected star, energy change */
    private static final int READOUTS = 5;

    @Test
    void writesThePositionsOfTheStarsOfBothGalaxiesFollowedByTheReadouts() {
        FloatBuffer frame = FloatBuffer.allocate(3 * 2 * COUNTS.stars() + READOUTS);

        simulation(COUNTS, 1, 60).writeFrame(frame);

        assertThat(frame.hasRemaining()).isFalse();
    }

    @Test
    void reportsThePairsOfAllParticlesAndTheFewerInteractionsThatTheTreeComputedInTheLastStep() {
        GalaxySimulation simulation = simulation(COUNTS, 1, 60);
        long particles = 2L * COUNTS.total();
        long pairs = particles * (particles - 1) / 2;

        float[] before = readouts(simulation, COUNTS);
        simulation.step(1.0 / 30);
        float[] after = readouts(simulation, COUNTS);

        assertThat(before[0]).as("pairs").isEqualTo((float) pairs);
        assertThat(before[1]).as("interactions before the first step").isZero();
        // every particle interacts at least with itself
        assertThat(after[1]).as("interactions").isBetween((float) particles, pairs / 4f);
    }

    @Test
    void reportsTheInteractionsOfTheSelectedStarInTheLastStep() {
        GalaxySimulation simulation = simulation(FEW, 1, 60);
        float particles = 2 * FEW.total();
        simulation.step(1.0 / 30);

        simulation.apply(new Select(2 * FEW.stars() - 1));

        float[] readouts = readouts(simulation, FEW);
        assertThat(readouts[2]).isBetween(1f, particles).isLessThan(readouts[1]);
    }

    @Test
    void reportsNothingOfAStarWhileNoneIsSelected() {
        GalaxySimulation simulation = simulation(FEW, 1, 60);
        simulation.step(1.0 / 30);
        simulation.apply(new Select(0));

        simulation.apply(new Deselect());

        float[] readouts = readouts(simulation, FEW);
        assertThat(readouts[2]).as("interactions").isNaN();
        assertThat(readouts[3]).as("error").isNaN();
    }

    @Test
    void reportsHowFarTheAccelerationOfTheSelectedStarDeviatesFromTheDirectSum() {
        GalaxySimulation simulation = simulation(FEW, 1, 60);
        simulation.step(1.0 / 30);

        simulation.apply(new Select(7));

        assertThat(readouts(simulation, FEW)[3]).as("error in percent").isStrictlyBetween(0f, 5f);
    }

    @Test
    void computesFewerInteractionsThatErrMoreWithALargerOpeningAngle() {
        GalaxySimulation simulation = simulation(FEW, 1, 60);
        simulation.apply(new Select(7));
        simulation.step(1.0 / 30);
        float[] fine = readouts(simulation, FEW);

        simulation.apply(new ParameterChange(GalaxyDefinition.OPENING_ANGLE, 1.5));
        simulation.step(1.0 / 30);
        float[] coarse = readouts(simulation, FEW);

        assertThat(coarse[1]).as("interactions").isLessThan(fine[1] / 2);
        assertThat(coarse[3]).as("error of the star").isGreaterThan(2 * fine[3]);
    }

    @Test
    void showsTheCubesThatActOnTheSelectedStarAsAWholeInTheLastStep() {
        GalaxySimulation simulation = simulation(FEW, 1, 60);
        simulation.step(1.0 / 30);
        simulation.apply(new Select(7));

        float[] xray = xray(simulation);

        int count = (int) xray[0];
        assertThat(count).isPositive().isLessThan((int) readouts(simulation, FEW)[2]);
        for (int cube = 0; cube < count; cube++) {
            assertThat(xray[4 + 4 * cube]).as("half the edge of cube %d", cube).isPositive();
        }
    }

    @Test
    void showsNoCubesWhileNoStarIsSelected() {
        GalaxySimulation simulation = simulation(FEW, 1, 60);
        simulation.step(1.0 / 30);

        assertThat(xray(simulation)[0]).isZero();
    }

    @Test
    void measuresTheChangeOfTheEnergyEveryThirtySteps() {
        GalaxySimulation simulation = simulation(FEW, 1, 60);
        float[] start = readouts(simulation, FEW);
        // a frame at this pace takes a single step
        for (int step = 1; step < GalaxySimulation.STEPS_PER_ENERGY; step++) {
            simulation.step(1.0 / 30);
        }
        float[] before = readouts(simulation, FEW);

        simulation.step(1.0 / 30);

        float energyError = readouts(simulation, FEW)[4];
        assertThat(start[4]).as("at the start").isZero();
        assertThat(before[4]).as("before the thirtieth step").isZero();
        assertThat(energyError).as("in percent, after thirty steps").isNotZero().isBetween(-1f, 1f);
    }

    // both galaxies of FEW have 1,200 stars in all
    @ParameterizedTest
    @ValueSource(ints = {-1, 1_200})
    void rejectsSelectingAStarThatDoesNotExist(int star) {
        GalaxySimulation simulation = simulation(FEW, 1, 60);

        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> simulation.apply(new Select(star)))
                .withMessage("There is no star %d", star);
    }

    @Test
    void startsWithTheGalaxiesApartOnEitherSideOfTheirCenterOfMass() {
        float[] frame = frame(simulation(COUNTS, 1, 60), COUNTS);

        double[] first = bulgeCenter(frame, COUNTS, 0);
        double[] second = bulgeCenter(frame, COUNTS, 1);
        // the bulges lie a bit off the centers of mass of their galaxies, which include the halos
        assertThat(distance(first, second)).isCloseTo(GalaxySimulation.START_DISTANCE, within(2.0));
        assertThat(distance(first, new double[3])).isCloseTo(distance(second, new double[3]), within(2.0));
    }

    @Test
    void letsTheGalaxiesPassThroughEachOtherAndMerge() {
        GalaxySimulation simulation = simulation(FEW, 1, 60);

        // distances between the bulges at every second of wall-clock time, 15 Myr apart
        double[] distances = new double[67];
        for (int second = 0; second < distances.length; second++) {
            float[] frame = frame(simulation, FEW);
            distances[second] = distance(bulgeCenter(frame, FEW, 0), bulgeCenter(frame, FEW, 1));
            simulation.step(1);
        }

        assertThat(distances[13])
                .as("distance after 195 Myr, at the pericenter")
                .isLessThan(2 * PERICENTER);
        assertThat(distances[27]).as("distance after 405 Myr").isGreaterThan(2 * PERICENTER);
        assertThat(distances[66]).as("distance after 990 Myr").isLessThan(5);
    }

    @Test
    void tiltsTheDiskOfTheSecondGalaxyByTheInclination() {
        float[] flat = frame(simulation(COUNTS, 1, 0), COUNTS);
        float[] upright = frame(simulation(COUNTS, 1, 90), COUNTS);

        double[] center = bulgeCenter(flat, COUNTS, 1);
        double spread = IntStream.range(COUNTS.stars(), COUNTS.stars() + COUNTS.disk())
                .mapToDouble(star -> square(flat[3 * star] - center[0]) + square(flat[3 * star + 1] - center[1]))
                .average()
                .orElseThrow();

        // turned upright the disk spreads along z as much as along each axis in its plane
        assertThat(meanSquaredHeight(flat)).isLessThan(0.15);
        assertThat(meanSquaredHeight(upright)).isCloseTo(spread / 2, withinPercentage(10));
    }

    @Test
    void scalesTheSecondGalaxyByTheCubeRootOfTheMassRatio() {
        float[] frame = frame(
                new GalaxySimulation(COUNTS, 1, PACE, 0.125, PERICENTER, 60, GalaxyDefinition.DEFAULT_OPENING_ANGLE),
                COUNTS);

        assertThat(diskSize(frame, 1) / diskSize(frame, 0)).isCloseTo(0.5, withinPercentage(10));
    }

    @Test
    void advancesTheSimulatedTimeAtThePace() {
        GalaxySimulation simulation = simulation(FEW, 1, 60);

        simulation.step(0.1);
        simulation.apply(new ParameterChange(GalaxyDefinition.PACE, 2 * PACE));
        simulation.step(0.05);
        simulation.step(0);

        assertThat(simulation.time()).isCloseTo(0.2 * PACE, within(1e-12));
    }

    @Test
    void repeatsItselfForTheSameSeedOnly() {
        float[] first = frameAfterASecond(simulation(FEW, 7, 60));
        float[] again = frameAfterASecond(simulation(FEW, 7, 60));
        float[] other = frameAfterASecond(simulation(FEW, 8, 60));

        assertThat(again).containsExactly(first);
        assertThat(other).isNotEqualTo(first);
    }

    @Test
    void rejectsGrabbingStars() {
        GalaxySimulation simulation = simulation(FEW, 1, 60);

        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> simulation.apply(new Grab(0, 1, 2, 3)));
    }

    @Test
    void rejectsChangesOfTheEncounterWhileTheGalaxiesCollide() {
        GalaxySimulation simulation = simulation(FEW, 1, 60);

        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> simulation.apply(new ParameterChange(GalaxyDefinition.RATIO, 0.5)))
                .withMessage("Parameter 'ratio' cannot change while the galaxies collide");
    }

    @Test
    void rejectsNegativeTimeSpans() {
        GalaxySimulation simulation = simulation(FEW, 1, 60);

        assertThatIllegalArgumentException().isThrownBy(() -> simulation.step(-1));
    }

    private static GalaxySimulation simulation(ParticleCounts counts, long seed, double inclination) {
        return new GalaxySimulation(
                counts, seed, PACE, 1, PERICENTER, inclination, GalaxyDefinition.DEFAULT_OPENING_ANGLE);
    }

    private static float[] frameAfterASecond(GalaxySimulation simulation) {
        simulation.step(1);
        return frame(simulation, FEW);
    }

    private static float[] frame(GalaxySimulation simulation, ParticleCounts counts) {
        return Arrays.copyOf(writtenFrame(simulation, counts), 3 * 2 * counts.stars());
    }

    private static float[] readouts(GalaxySimulation simulation, ParticleCounts counts) {
        float[] frame = writtenFrame(simulation, counts);
        return Arrays.copyOfRange(frame, frame.length - READOUTS, frame.length);
    }

    private static float[] xray(GalaxySimulation simulation) {
        FloatBuffer xray = FloatBuffer.allocate(1 + 4 * GalaxyDefinition.CUBE_CAPACITY);
        simulation.writeXray(xray);
        assertThat(xray.hasRemaining()).as("floats missing from the x-ray").isFalse();
        return xray.array();
    }

    private static float[] writtenFrame(GalaxySimulation simulation, ParticleCounts counts) {
        FloatBuffer frame = FloatBuffer.allocate(3 * 2 * counts.stars() + READOUTS);
        simulation.writeFrame(frame);
        return frame.array();
    }

    /** mean position of the bulge stars of the first or the second galaxy */
    private static double[] bulgeCenter(float[] frame, ParticleCounts counts, int galaxy) {
        int first = galaxy * counts.stars() + counts.disk();
        double[] center = new double[3];
        for (int star = first; star < first + counts.bulge(); star++) {
            for (int axis = 0; axis < 3; axis++) {
                center[axis] += frame[3 * star + axis] / counts.bulge();
            }
        }
        return center;
    }

    /** RMS distance of a disk's stars from the center of its bulge */
    private static double diskSize(float[] frame, int galaxy) {
        double[] center = bulgeCenter(frame, COUNTS, galaxy);
        int first = galaxy * COUNTS.stars();
        return Math.sqrt(IntStream.range(first, first + COUNTS.disk())
                .mapToDouble(star -> square(frame[3 * star] - center[0])
                        + square(frame[3 * star + 1] - center[1])
                        + square(frame[3 * star + 2] - center[2]))
                .average()
                .orElseThrow());
    }

    /** mean squared height of the second disk's stars above the plane of the orbit */
    private static double meanSquaredHeight(float[] frame) {
        return IntStream.range(COUNTS.stars(), COUNTS.stars() + COUNTS.disk())
                .mapToDouble(star -> square(frame[3 * star + 2]))
                .average()
                .orElseThrow();
    }

    private static double distance(double[] a, double[] b) {
        return Math.sqrt(square(a[0] - b[0]) + square(a[1] - b[1]) + square(a[2] - b[2]));
    }

    private static double square(double value) {
        return value * value;
    }
}
