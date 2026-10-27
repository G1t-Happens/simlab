package dev.simlab.core.toolkit.nbody;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LeapfrogTest {

    private static final double SOFTENING = 0.05;

    /** speed at which the softened gravity of two particles of mass 1 keeps them on a circle of radius 1 */
    private static final double ORBITAL_SPEED = Math.sqrt(2 / Math.pow(4 + SOFTENING * SOFTENING, 1.5));

    private static final double PERIOD = 2 * Math.PI / ORBITAL_SPEED;

    @Test
    void keepsTwoParticlesOnACircularOrbit() {
        assertThat(orbitError(1_000)).isLessThan(1e-3);
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 200, 400})
    void doublingTheStepsReducesTheErrorToAQuarter(int steps) {
        double ratio = orbitError(steps) / orbitError(2 * steps);

        assertThat(ratio).isCloseTo(4, within(0.1));
    }

    @Test
    void conservesMomentumAndEnergy() {
        Cluster cluster = new Cluster(300, 1);
        double[] momentum = cluster.momentum();
        double energy = cluster.energy(SOFTENING);
        var leapfrog = new Leapfrog(new BarnesHut(SOFTENING, 0));

        for (int step = 0; step < 1_000; step++) {
            leapfrog.step(cluster.position, cluster.velocity, cluster.mass, 0.002);
        }

        assertThat(cluster.momentum()).containsExactly(momentum, within(1e-12));
        assertThat(cluster.energy(SOFTENING)).isCloseTo(energy, within(1e-5 * Math.abs(energy)));
    }

    @Test
    void retracesItsStepsBackwards() {
        Cluster cluster = new Cluster(300, 2);
        double[] start = cluster.position.clone();
        var leapfrog = new Leapfrog(new BarnesHut(SOFTENING, 0.7));

        for (int step = 0; step < 100; step++) {
            leapfrog.step(cluster.position, cluster.velocity, cluster.mass, 0.002);
        }
        for (int step = 0; step < 100; step++) {
            leapfrog.step(cluster.position, cluster.velocity, cluster.mass, -0.002);
        }

        assertThat(cluster.position).containsExactly(start, within(1e-12));
    }

    @Test
    void rejectsVelocitiesThatDoNotFitThePositions() {
        var leapfrog = new Leapfrog(new BarnesHut(SOFTENING, 0.7));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> leapfrog.step(new double[6], new double[3], new double[2], 0.1));
    }

    /**
     * Moves two particles of mass 1 once around a circular orbit of radius 1 and returns how far the first one
     * ends up from where it started
     */
    private static double orbitError(int steps) {
        double[] position = {-1, 0, 0, 1, 0, 0};
        double[] velocity = {0, -ORBITAL_SPEED, 0, 0, ORBITAL_SPEED, 0};
        var leapfrog = new Leapfrog(new BarnesHut(SOFTENING, 0.7));
        for (int step = 0; step < steps; step++) {
            leapfrog.step(position, velocity, new double[] {1, 1}, PERIOD / steps);
        }
        return Math.hypot(position[0] + 1, position[1]);
    }
}
