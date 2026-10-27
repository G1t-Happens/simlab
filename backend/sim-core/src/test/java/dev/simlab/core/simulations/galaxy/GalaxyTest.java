package dev.simlab.core.simulations.galaxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

import dev.simlab.core.toolkit.nbody.BarnesHut;
import dev.simlab.core.toolkit.nbody.Leapfrog;
import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GalaxyTest {

    private static final GalaxyModel MODEL = GalaxySimulation.MODEL;
    private static final double SOFTENING = GalaxySimulation.SOFTENING;
    private static final ParticleCounts COUNTS = new ParticleCounts(4_000, 800, 4_000);

    private final Galaxy galaxy = Galaxy.sample(MODEL, COUNTS, SOFTENING, new SplittableRandom(1));

    @Test
    void givesTheDiskTheBulgeAndTheHaloTheirMasses() {
        assertThat(massOf(0, COUNTS.disk())).isCloseTo(MODEL.disk().mass(), within(1e-9));
        assertThat(massOf(COUNTS.disk(), COUNTS.bulge()))
                .isCloseTo(MODEL.bulge().mass(), within(1e-9));
        assertThat(massOf(COUNTS.stars(), COUNTS.halo())).isCloseTo(MODEL.halo().mass(), within(1e-9));
    }

    @Test
    void restsWithItsCenterOfMassAtTheOrigin() {
        for (int axis = 0; axis < 3; axis++) {
            assertThat(weightedSum(galaxy.position, axis)).as("center of mass").isCloseTo(0, within(1e-12));
            assertThat(weightedSum(galaxy.velocity, axis)).as("momentum").isCloseTo(0, within(1e-12));
        }
    }

    @Test
    void ordersTheStarsOfTheDiskFromItsCenterOutwards() {
        int quarter = COUNTS.disk() / 4;

        double[] meanDistances = IntStream.range(0, 4)
                .mapToDouble(part -> IntStream.range(part * quarter, (part + 1) * quarter)
                        .mapToDouble(this::distanceFromAxis)
                        .average()
                        .orElseThrow())
                .toArray();

        assertThat(meanDistances).isSorted();
    }

    @Test
    void keepsTheDiskAsThinAsItsScaleHeight() {
        // with the density sech²(z/z₀) the heights spread by π z₀ / √12, see GalaxyModelTest
        assertThat(rmsHeight()).isCloseTo(Math.PI * MODEL.disk().scaleHeight() / Math.sqrt(12), withinPercentage(5));
    }

    @Test
    void turnsTheDiskCounterclockwiseAtNearlyTheSpeedOfCircularOrbits() {
        RadialGravity gravity = RadialGravity.measure(
                galaxy.position,
                galaxy.mass,
                SOFTENING,
                0.03,
                1.5 * MODEL.halo().radius());
        double distance = 2 * MODEL.disk().scaleLength();

        // random motions make the stars lag a bit behind circular orbits (asymmetric drift)
        double speedRatio = IntStream.range(0, COUNTS.disk())
                .filter(star -> Math.abs(distanceFromAxis(star) - distance) < 0.5)
                .mapToDouble(
                        star -> azimuthalSpeed(star) / Math.sqrt(gravity.circularSpeedSquared(distanceFromAxis(star))))
                .average()
                .orElseThrow();
        assertThat(speedRatio).isBetween(0.9, 1.0);
    }

    @Test
    void startsInVirialEquilibrium() {
        double kinetic = 0;
        double potential = 0;
        for (int a = 0; a < galaxy.mass.length; a++) {
            int i = 3 * a;
            kinetic += galaxy.mass[a]
                    * (square(galaxy.velocity[i]) + square(galaxy.velocity[i + 1]) + square(galaxy.velocity[i + 2]))
                    / 2;
            for (int b = a + 1; b < galaxy.mass.length; b++) {
                int j = 3 * b;
                double distanceSquared = square(galaxy.position[j] - galaxy.position[i])
                        + square(galaxy.position[j + 1] - galaxy.position[i + 1])
                        + square(galaxy.position[j + 2] - galaxy.position[i + 2]);
                potential -= galaxy.mass[a] * galaxy.mass[b] / Math.sqrt(distanceSquared + SOFTENING * SOFTENING);
            }
        }

        assertThat(2 * kinetic / -potential).as("virial ratio").isCloseTo(1, within(0.05));
    }

    @Test
    void keepsItsShapeForAHundredMillionYears() {
        double diskRadius = halfMassRadius(0, COUNTS.disk());
        double bulgeRadius = halfMassRadius(COUNTS.disk(), COUNTS.bulge());
        double haloRadius = halfMassRadius(COUNTS.stars(), COUNTS.halo());
        double thickness = rmsHeight();
        var leapfrog = new Leapfrog(new BarnesHut(SOFTENING, GalaxyDefinition.DEFAULT_OPENING_ANGLE));

        double step = GalaxySimulation.MAX_STEP;
        for (int i = 0; i < Math.round(100 / GalaxySimulation.MYR_PER_TIME_UNIT / step); i++) {
            leapfrog.step(galaxy.position, galaxy.velocity, galaxy.mass, step);
        }

        assertThat(halfMassRadius(0, COUNTS.disk())).as("disk").isCloseTo(diskRadius, withinPercentage(5));
        assertThat(halfMassRadius(COUNTS.disk(), COUNTS.bulge()))
                .as("bulge")
                .isCloseTo(bulgeRadius, withinPercentage(10));
        assertThat(halfMassRadius(COUNTS.stars(), COUNTS.halo()))
                .as("halo")
                .isCloseTo(haloRadius, withinPercentage(10));
        assertThat(rmsHeight()).as("thickness of the disk").isLessThan(1.3 * thickness);
    }

    @Test
    void turnsAboutAnAxisThroughTheOrigin() {
        double[] position = galaxy.position.clone();
        double[] velocity = galaxy.velocity.clone();

        galaxy.turn(0, 0, 2, Math.PI / 2);

        // a quarter turn about the z axis takes x to y and y to -x
        for (int i = 0; i < position.length; i += 3) {
            assertThat(Arrays.copyOfRange(galaxy.position, i, i + 3))
                    .containsExactly(new double[] {-position[i + 1], position[i], position[i + 2]}, within(1e-12));
            assertThat(Arrays.copyOfRange(galaxy.velocity, i, i + 3))
                    .containsExactly(new double[] {-velocity[i + 1], velocity[i], velocity[i + 2]}, within(1e-12));
        }
    }

    @Test
    void movesAllOfItsParticlesAndChangesTheirVelocities() {
        double[] position = galaxy.position.clone();
        double[] velocity = galaxy.velocity.clone();

        galaxy.move(1, 2, 3, 4, 5, 6);

        for (int i = 0; i < position.length; i += 3) {
            assertThat(Arrays.copyOfRange(galaxy.position, i, i + 3))
                    .containsExactly(position[i] + 1, position[i + 1] + 2, position[i + 2] + 3);
            assertThat(Arrays.copyOfRange(galaxy.velocity, i, i + 3))
                    .containsExactly(velocity[i] + 4, velocity[i + 1] + 5, velocity[i + 2] + 6);
        }
    }

    private double massOf(int first, int count) {
        return Arrays.stream(galaxy.mass, first, first + count).sum();
    }

    private double weightedSum(double[] vectors, int axis) {
        return IntStream.range(0, galaxy.mass.length)
                .mapToDouble(particle -> galaxy.mass[particle] * vectors[3 * particle + axis])
                .sum();
    }

    private double distanceFromAxis(int particle) {
        return Math.hypot(galaxy.position[3 * particle], galaxy.position[3 * particle + 1]);
    }

    /** speed of a star around the axis, positive counterclockwise seen from above */
    private double azimuthalSpeed(int star) {
        int i = 3 * star;
        return (galaxy.position[i] * galaxy.velocity[i + 1] - galaxy.position[i + 1] * galaxy.velocity[i])
                / distanceFromAxis(star);
    }

    /** RMS height of the disk stars above their midplane, where the center of mass of the whole galaxy need not lie */
    private double rmsHeight() {
        double midplane = IntStream.range(0, COUNTS.disk())
                .mapToDouble(star -> galaxy.position[3 * star + 2])
                .average()
                .orElseThrow();
        return Math.sqrt(IntStream.range(0, COUNTS.disk())
                .mapToDouble(star -> square(galaxy.position[3 * star + 2] - midplane))
                .average()
                .orElseThrow());
    }

    /** distance from the origin within which half of the given particles lie */
    private double halfMassRadius(int first, int count) {
        double[] radii = IntStream.range(first, first + count)
                .mapToDouble(particle -> Math.sqrt(square(galaxy.position[3 * particle])
                        + square(galaxy.position[3 * particle + 1])
                        + square(galaxy.position[3 * particle + 2])))
                .sorted()
                .toArray();
        return radii[count / 2];
    }

    private static double square(double value) {
        return value * value;
    }
}
