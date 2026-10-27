package dev.simlab.core.simulations.galaxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

import dev.simlab.core.simulations.galaxy.GalaxyModel.Disk;
import dev.simlab.core.simulations.galaxy.GalaxyModel.Spheroid;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.DoubleUnaryOperator;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class GalaxyModelTest {

    private static final Disk DISK = new Disk(4, 3, 0.3, 18);
    private static final Spheroid BULGE = new Spheroid(1, 0.6, 6);
    private static final Spheroid HALO = new Spheroid(12, 8, 40);
    private static final int SAMPLES = 100_000;

    @Test
    void spreadsTheMassOfTheDiskOverItsSurface() {
        DoubleUnaryOperator massPerDistance = distance -> DISK.surfaceDensity(distance) * 2 * Math.PI * distance;

        assertThat(integrate(massPerDistance, DISK.radius())).isCloseTo(DISK.mass(), within(1e-6));
        assertThat(DISK.surfaceDensity(DISK.radius() + 1)).isZero();
    }

    @Test
    void drawsTheDistancesOfStarsFromTheAxisAsTheSurfaceDensityFallsOff() {
        var random = new SplittableRandom(1);
        double[] distances = new double[SAMPLES];
        Arrays.setAll(distances, sample -> DISK.sampleDistance(random));
        Arrays.sort(distances);

        double halfMassDistance = radiusHolding(
                DISK.mass() / 2, distance -> DISK.surfaceDensity(distance) * 2 * Math.PI * distance, DISK.radius());
        assertThat(distances[SAMPLES / 2]).isCloseTo(halfMassDistance, withinPercentage(1.5));
        assertThat(distances[SAMPLES - 1]).isLessThanOrEqualTo(DISK.radius());
    }

    @Test
    void drawsTheHeightsOfStarsAsThickAsTheDisk() {
        var random = new SplittableRandom(2);
        double[] heights = new double[SAMPLES];
        Arrays.setAll(heights, sample -> DISK.sampleHeight(random));

        // with the density sech²(z/z₀), z/z₀ is half a logistic variable whose variance is π²/3
        double rms = Math.sqrt(
                Arrays.stream(heights).map(height -> height * height).average().orElseThrow());
        assertThat(rms).isCloseTo(Math.PI * DISK.scaleHeight() / Math.sqrt(12), withinPercentage(1));
        assertThat(Arrays.stream(heights).average().orElseThrow()).isCloseTo(0, within(0.01 * DISK.scaleHeight()));
    }

    @Test
    void drawsAgainRatherThanPlacingAStarInfinitelyFarFromThePlane() {
        // random numbers 0 and then 1/2, which lies in the middle of the distribution of heights
        Iterator<Long> values = List.of(0L, Long.MIN_VALUE).iterator();
        RandomGenerator random = values::next;

        assertThat(DISK.sampleHeight(random)).isZero();
    }

    @Test
    void spreadsTheMassOfASpheroidOverItsVolume() {
        DoubleUnaryOperator massPerRadius = radius -> BULGE.density(radius) * 4 * Math.PI * radius * radius;

        assertThat(integrate(massPerRadius, BULGE.radius())).isCloseTo(BULGE.mass(), within(1e-6));
        assertThat(BULGE.density(BULGE.radius() + 1)).isZero();
    }

    @Test
    void drawsTheRadiiOfParticlesAsTheDensityOfTheSpheroidFallsOff() {
        var random = new SplittableRandom(3);
        double[] radii = new double[SAMPLES];
        Arrays.setAll(radii, sample -> HALO.sampleDistance(random));
        Arrays.sort(radii);

        double halfMassRadius = radiusHolding(
                HALO.mass() / 2, radius -> HALO.density(radius) * 4 * Math.PI * radius * radius, HALO.radius());
        assertThat(radii[SAMPLES / 2]).isCloseTo(halfMassRadius, withinPercentage(1.5));
        assertThat(radii[SAMPLES - 1]).isLessThanOrEqualTo(HALO.radius());
    }

    @Test
    void addsUpTheMassesOfItsParts() {
        assertThat(new GalaxyModel(DISK, BULGE, HALO).mass()).isEqualTo(17);
    }

    @Test
    void scalesItsMassesByAFactorAndItsLengthsByTheCubeRootOfTheFactor() {
        GalaxyModel scaled = new GalaxyModel(DISK, BULGE, HALO).scaled(0.125);

        assertThat(scaled)
                .isEqualTo(new GalaxyModel(
                        new Disk(0.5, 1.5, 0.15, 9), new Spheroid(0.125, 0.3, 3), new Spheroid(1.5, 4, 20)));
    }

    /** midpoint rule from 0 to the limit, never evaluates the function at 0 */
    private static double integrate(DoubleUnaryOperator function, double limit) {
        int intervals = 100_000;
        double width = limit / intervals;
        double sum = 0;
        for (int interval = 0; interval < intervals; interval++) {
            sum += function.applyAsDouble((interval + 0.5) * width);
        }
        return sum * width;
    }

    /** radius within which a density per radius holds the given mass, by bisection */
    private static double radiusHolding(double mass, DoubleUnaryOperator massPerRadius, double limit) {
        double low = 0;
        double high = limit;
        while (high - low > 1e-6 * limit) {
            double middle = (low + high) / 2;
            if (integrate(massPerRadius, middle) < mass) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return low;
    }
}
