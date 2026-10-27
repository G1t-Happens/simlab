package dev.simlab.core.toolkit.nbody;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

import java.util.Arrays;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BarnesHutTest {

    private static final double SOFTENING = 0.05;
    private static final double OPENING_ANGLE = 0.7;

    @Test
    void pullsTwoParticlesTowardsEachOtherWithSoftenedGravity() {
        double[] acceleration = accelerations(new double[] {0, 0, 0, 0, 3, 0}, 2, 5);

        // a = G m r / (r² + ε²)^(3/2) towards the other particle
        double pull = 3 / Math.pow(9 + SOFTENING * SOFTENING, 1.5);
        assertThat(acceleration).containsExactly(new double[] {0, 5 * pull, 0, 0, -2 * pull, 0}, within(1e-15));
    }

    @Test
    void sumsTheGravityOfEveryPairWithoutAnOpeningAngle() {
        Cluster cluster = new Cluster(1_000, 1);
        double[] acceleration = new double[cluster.position.length];

        new BarnesHut(SOFTENING, 0).accelerate(cluster.position, cluster.mass, acceleration);

        assertThat(acceleration).containsExactly(cluster.directSum(SOFTENING), within(1e-12));
    }

    @Test
    void approximatesGravityToAboutHalfAPercent() {
        Cluster cluster = new Cluster(5_000, 2);
        double[] exact = cluster.directSum(SOFTENING);

        double[] acceleration = accelerations(cluster.position, cluster.mass);

        double[] errors = new double[cluster.mass.length];
        Arrays.setAll(errors, particle -> distance(acceleration, exact, particle) / length(exact, particle));
        Arrays.sort(errors);
        assertThat(errors[errors.length / 2]).as("median relative error").isLessThan(0.006);
        assertThat(errors[errors.length * 99 / 100]).as("99th percentile").isLessThan(0.03);
    }

    @Test
    void exertsNoForceOnALoneParticle() {
        assertThat(accelerations(new double[] {1, 2, 3}, 4)).containsOnly(0);
    }

    @Test
    void letsParticlesWithoutMassFeelGravityButExertNone() {
        double[] acceleration = accelerations(new double[] {0, 0, 0, 2, 0, 0}, 1, 0);

        assertThat(acceleration)
                .containsExactly(
                        new double[] {0, 0, 0, -2 / Math.pow(4 + SOFTENING * SOFTENING, 1.5), 0, 0}, within(1e-15));
    }

    @Test
    void handlesMoreParticlesAtOnePointThanACubeHolds() {
        int count = 100;
        double[] position = new double[3 * (count + 1)];
        Arrays.fill(position, 3, position.length, 1);
        double[] mass = new double[count + 1];
        Arrays.fill(mass, 1);

        double[] acceleration = accelerations(position, mass);

        // particles at one point pull on each other with no force, at distance 0
        double pull = 1 / Math.pow(3 + SOFTENING * SOFTENING, 1.5);
        assertThat(Arrays.copyOf(acceleration, 3))
                .as("acceleration of the particle at the origin")
                .containsExactly(new double[] {count * pull, count * pull, count * pull}, within(1e-12));
        for (int i = 3; i < acceleration.length; i++) {
            assertThat(acceleration[i]).isCloseTo(-pull, within(1e-12));
        }
    }

    @Test
    void givesTheSameAccelerationsAndPotentialEnergyOnEveryRun() {
        Cluster cluster = new Cluster(20_000, 3);

        double[] first = accelerations(cluster.position, cluster.mass);
        double[] again = accelerations(cluster.position, cluster.mass);

        assertThat(again).containsExactly(first);
        assertThat(new BarnesHut(SOFTENING, OPENING_ANGLE).potentialEnergy(cluster.position, cluster.mass))
                .isEqualTo(new BarnesHut(SOFTENING, OPENING_ANGLE).potentialEnergy(cluster.position, cluster.mass));
    }

    @Test
    void followsParticlesThatMoveAndChangeInNumber() {
        var gravity = new BarnesHut(SOFTENING, OPENING_ANGLE);
        Cluster start = new Cluster(5_000, 4);
        Cluster moved = new Cluster(5_000, 5);
        Cluster fewer = new Cluster(3_000, 6);
        gravity.accelerate(start.position, start.mass, new double[start.position.length]);

        for (Cluster cluster : new Cluster[] {moved, fewer}) {
            double[] acceleration = new double[cluster.position.length];
            gravity.accelerate(cluster.position, cluster.mass, acceleration);

            assertThat(acceleration).containsExactly(accelerations(cluster.position, cluster.mass), within(1e-12));
        }
    }

    @Test
    void countsTheInteractionsOfEveryParticleWithEveryOneWithoutAnOpeningAngle() {
        Cluster cluster = new Cluster(1_000, 1);
        var gravity = new BarnesHut(SOFTENING, 0);

        gravity.accelerate(cluster.position, cluster.mass, new double[cluster.position.length]);

        assertThat(gravity.interactions()).isEqualTo(1_000L * 1_000);
        assertThat(gravity.interactionsOf(0)).isEqualTo(1_000);
        assertThat(gravity.interactionsOf(999)).isEqualTo(1_000);
    }

    @Test
    void countsFewerInteractionsThanPairsWithAnOpeningAngle() {
        Cluster cluster = new Cluster(5_000, 2);
        var gravity = new BarnesHut(SOFTENING, OPENING_ANGLE);

        gravity.accelerate(cluster.position, cluster.mass, new double[cluster.position.length]);

        long total =
                IntStream.range(0, 5_000).mapToLong(gravity::interactionsOf).sum();
        long pairs = 5_000L * 4_999 / 2;
        assertThat(gravity.interactions()).isEqualTo(total).isLessThan(pairs / 2);
    }

    @Test
    void countsNoInteractionsOfParticlesThatTheLastComputationDidNotInclude() {
        var gravity = new BarnesHut(SOFTENING, OPENING_ANGLE);

        assertThat(gravity.interactions())
                .as("interactions before the first computation")
                .isZero();
        assertThat(gravity.interactionsOf(0)).isZero();

        Cluster cluster = new Cluster(100, 3);
        gravity.accelerate(cluster.position, cluster.mass, new double[cluster.position.length]);

        assertThat(gravity.interactionsOf(100)).isZero();
    }

    @Test
    void sumsThePotentialEnergyOfEveryPairWithoutAnOpeningAngle() {
        Cluster cluster = new Cluster(1_000, 1);

        double energy = new BarnesHut(SOFTENING, 0).potentialEnergy(cluster.position, cluster.mass);

        assertThat(energy).isCloseTo(cluster.potentialEnergy(SOFTENING), withinPercentage(1e-10));
    }

    @Test
    void approximatesThePotentialEnergyMoreCloselyThanTheForces() {
        Cluster cluster = new Cluster(5_000, 2);

        double energy = new BarnesHut(SOFTENING, OPENING_ANGLE).potentialEnergy(cluster.position, cluster.mass);

        assertThat(energy).isCloseTo(cluster.potentialEnergy(SOFTENING), withinPercentage(0.1));
    }

    @Test
    void findsNoPotentialEnergyOfALoneParticleOrOfNone() {
        var gravity = new BarnesHut(SOFTENING, OPENING_ANGLE);

        assertThat(gravity.potentialEnergy(new double[] {1, 2, 3, 4, 5, 6}, new double[] {4, 0}))
                .isZero();
        assertThat(gravity.potentialEnergy(new double[0], new double[0])).isZero();
    }

    @Test
    void countsTheInteractionsOfAComputationOfThePotentialEnergyButKnowsNoErrorsOfAccelerations() {
        Cluster cluster = new Cluster(5_000, 2);
        var gravity = new BarnesHut(SOFTENING, OPENING_ANGLE);
        gravity.accelerate(cluster.position, cluster.mass, new double[cluster.position.length]);
        long interactions = gravity.interactions();

        gravity.potentialEnergy(cluster.position, cluster.mass);

        assertThat(gravity.interactions()).isEqualTo(interactions);
        assertThat(gravity.relativeErrorOf(7)).isNaN();
    }

    @Test
    void measuresTheRelativeErrorOfTheAccelerationOfAParticleAgainstTheDirectSum() {
        Cluster cluster = new Cluster(5_000, 2);
        double[] exact = cluster.directSum(SOFTENING);
        var gravity = new BarnesHut(SOFTENING, OPENING_ANGLE);
        double[] acceleration = new double[cluster.position.length];

        gravity.accelerate(cluster.position, cluster.mass, acceleration);

        for (int particle : new int[] {0, 1, 2, 4_999}) {
            double error = distance(acceleration, exact, particle) / length(exact, particle);
            assertThat(gravity.relativeErrorOf(particle)).isCloseTo(error, within(1e-9));
        }
    }

    @Test
    void measuresNoErrorWithoutAnOpeningAngle() {
        Cluster cluster = new Cluster(1_000, 1);
        var gravity = new BarnesHut(SOFTENING, 0);

        gravity.accelerate(cluster.position, cluster.mass, new double[cluster.position.length]);

        assertThat(gravity.relativeErrorOf(7)).isLessThan(1e-12);
    }

    @Test
    void measuresNoErrorOfParticlesThatTheLastComputationDidNotInclude() {
        var gravity = new BarnesHut(SOFTENING, OPENING_ANGLE);

        assertThat(gravity.relativeErrorOf(0)).isNaN();

        Cluster cluster = new Cluster(100, 3);
        gravity.accelerate(cluster.position, cluster.mass, new double[cluster.position.length]);

        assertThat(gravity.relativeErrorOf(100)).isNaN();
    }

    @Test
    void listsTheCubesThatHoldAFarClusterAndActOnAParticleAsAWhole() {
        // 20 particles near the origin and 60 particles 1,000 away along x
        int count = 80;
        double[] position = new double[3 * count];
        for (int particle = 0; particle < count; particle++) {
            position[3 * particle] = (particle < 20 ? 0 : 1_000) + particle % 5;
            position[3 * particle + 1] = particle % 4;
            position[3 * particle + 2] = particle % 3;
        }
        double[] mass = new double[count];
        Arrays.fill(mass, 1);
        var gravity = new BarnesHut(SOFTENING, OPENING_ANGLE);
        gravity.accelerate(position, mass, new double[position.length]);
        double[] cubes = new double[4 * count];

        int cubeCount = gravity.cubesActingOn(0, cubes);

        assertThat(cubeCount).isPositive();
        for (int particle = 0; particle < count; particle++) {
            assertThat(isInACube(position, particle, cubes, cubeCount))
                    .as("particle %d in a cube", particle)
                    .isEqualTo(particle >= 20);
        }
    }

    @Test
    void countsMoreCubesThanFitWithoutWritingThem() {
        Cluster cluster = new Cluster(5_000, 2);
        var gravity = new BarnesHut(SOFTENING, OPENING_ANGLE);
        gravity.accelerate(cluster.position, cluster.mass, new double[cluster.position.length]);
        double[] all = new double[4 * 5_000];
        int count = gravity.cubesActingOn(7, all);
        double[] two = new double[8];

        assertThat(gravity.cubesActingOn(7, two)).isEqualTo(count).isGreaterThan(2);
        assertThat(two).containsExactly(Arrays.copyOf(all, 8));
        assertThat(gravity.cubesActingOn(7, new double[0])).isEqualTo(count);
    }

    @Test
    void listsNoCubesWithoutAnOpeningAngleOrBeforeTheFirstComputation() {
        Cluster cluster = new Cluster(1_000, 1);
        var gravity = new BarnesHut(SOFTENING, 0);
        double[] cubes = new double[4 * 1_000];

        assertThat(gravity.cubesActingOn(0, cubes))
                .as("before the first computation")
                .isZero();

        gravity.accelerate(cluster.position, cluster.mass, new double[cluster.position.length]);

        assertThat(gravity.cubesActingOn(0, cubes)).isZero();
        assertThat(gravity.cubesActingOn(1_000, cubes))
                .as("a particle that does not exist")
                .isZero();
    }

    @Test
    void reportsOnTheLastComputationWithItsOpeningAngleWhileTheNextOneTakesAnother() {
        Cluster cluster = new Cluster(5_000, 2);
        var gravity = new BarnesHut(SOFTENING, OPENING_ANGLE);
        gravity.accelerate(cluster.position, cluster.mass, new double[cluster.position.length]);
        double[] cubes = new double[4 * 5_000];
        int count = gravity.cubesActingOn(7, cubes);

        gravity.setOpeningAngle(0);

        assertThat(gravity.cubesActingOn(7, cubes)).isEqualTo(count).isPositive();
    }

    @Test
    void changesItsOpeningAngleForTheComputationsToCome() {
        Cluster cluster = new Cluster(1_000, 1);
        var gravity = new BarnesHut(SOFTENING, OPENING_ANGLE);
        double[] acceleration = new double[cluster.position.length];

        gravity.setOpeningAngle(0);
        gravity.accelerate(cluster.position, cluster.mass, acceleration);

        assertThat(acceleration).containsExactly(cluster.directSum(SOFTENING), within(1e-12));
    }

    @Test
    void acceptsNoParticles() {
        assertThat(accelerations(new double[0])).isEmpty();
    }

    @Test
    void rejectsArraysThatDoNotFitTheNumberOfParticles() {
        var gravity = new BarnesHut(SOFTENING, OPENING_ANGLE);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> gravity.accelerate(new double[3], new double[2], new double[6]));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> gravity.accelerate(new double[6], new double[2], new double[3]));
        assertThatIllegalArgumentException().isThrownBy(() -> gravity.potentialEnergy(new double[3], new double[2]));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
    void requiresAPositiveSoftening(double softening) {
        assertThatIllegalArgumentException().isThrownBy(() -> new BarnesHut(softening, OPENING_ANGLE));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, Double.NaN, Double.POSITIVE_INFINITY})
    void requiresAnOpeningAngleThatIsNotNegative(double openingAngle) {
        var gravity = new BarnesHut(SOFTENING, OPENING_ANGLE);

        assertThatIllegalArgumentException().isThrownBy(() -> new BarnesHut(SOFTENING, openingAngle));
        assertThatIllegalArgumentException().isThrownBy(() -> gravity.setOpeningAngle(openingAngle));
    }

    private static double[] accelerations(double[] position, double... mass) {
        double[] acceleration = new double[position.length];
        new BarnesHut(SOFTENING, OPENING_ANGLE).accelerate(position, mass, acceleration);
        return acceleration;
    }

    /** whether a particle lies in one of the cubes, given by their centers and half their edges */
    private static boolean isInACube(double[] position, int particle, double[] cubes, int cubeCount) {
        return IntStream.range(0, cubeCount)
                .anyMatch(cube -> IntStream.range(0, 3)
                        .allMatch(axis -> Math.abs(position[3 * particle + axis] - cubes[4 * cube + axis])
                                <= cubes[4 * cube + 3]));
    }

    private static double distance(double[] a, double[] b, int particle) {
        int i = 3 * particle;
        return Math.sqrt(square(a[i] - b[i]) + square(a[i + 1] - b[i + 1]) + square(a[i + 2] - b[i + 2]));
    }

    private static double length(double[] vectors, int particle) {
        int i = 3 * particle;
        return Math.sqrt(square(vectors[i]) + square(vectors[i + 1]) + square(vectors[i + 2]));
    }

    private static double square(double value) {
        return value * value;
    }
}
