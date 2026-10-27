package dev.simlab.core.simulations.galaxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

import dev.simlab.core.simulations.galaxy.GalaxyModel.Spheroid;
import java.util.function.DoubleUnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Gravity of a single heavy particle at the origin, i.e. that of a point mass */
class RadialGravityTest {

    private static final double MASS = 1_000;
    private static final double INNER_RADIUS = 0.05;
    private static final double OUTER_RADIUS = 200;

    private final RadialGravity gravity =
            RadialGravity.measure(new double[3], new double[] {MASS}, 0.001, INNER_RADIUS, OUTER_RADIUS);

    @ParameterizedTest
    @ValueSource(doubles = {0.1, 1, 10, 100})
    void measuresTheSpeedOfCircularOrbits(double radius) {
        assertThat(gravity.circularSpeedSquared(radius)).isCloseTo(MASS / radius, withinPercentage(0.5));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.1, 1, 10, 100})
    void findsTheEpicyclesOfKeplerOrbits(double radius) {
        // around a point mass a non-circular orbit closes on itself after one turn: κ = Ω
        assertThat(gravity.epicyclicRatio(radius)).isCloseTo(0.25, withinPercentage(1));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.1, 1, 10, 100})
    void measuresTheSpeedOfEscape(double radius) {
        assertThat(gravity.escapeSpeedSquared(radius)).isCloseTo(2 * MASS / radius, withinPercentage(0.5));
    }

    @ParameterizedTest
    @ValueSource(doubles = {1, 3, 10})
    void keepsASpheroidInEquilibriumWithTheDispersionOfTheJeansEquation(double radius) {
        // far beyond its scale length the spheroid's density falls off as r⁻⁴, for which the Jeans equation in
        // the gravity of a point mass gives σ² = G M / 5r
        DoubleUnaryOperator dispersionSquared = gravity.jeansDispersionSquared(new Spheroid(1, 0.001, 1_000));

        assertThat(dispersionSquared.applyAsDouble(radius)).isCloseTo(MASS / (5 * radius), withinPercentage(1));
    }

    @Test
    void assumesRigidRotationWhereNothingPulls() {
        RadialGravity massless =
                RadialGravity.measure(new double[3], new double[] {0}, 0.001, INNER_RADIUS, OUTER_RADIUS);

        assertThat(massless.circularSpeedSquared(1)).isZero();
        assertThat(massless.epicyclicRatio(1)).isOne();
    }

    @Test
    void takesTheLogarithmicMeanOfPositiveNumbers() {
        // (e - 1) / ln(e / 1) = e - 1
        assertThat(RadialGravity.logarithmicMean(Math.E, 1)).isCloseTo(Math.E - 1, within(1e-15));
        assertThat(RadialGravity.logarithmicMean(1, Math.E)).isCloseTo(Math.E - 1, within(1e-15));
        assertThat(RadialGravity.logarithmicMean(2, 2)).isEqualTo(2);
        assertThat(RadialGravity.logarithmicMean(2, 2 + 1e-12)).isCloseTo(2, within(1e-11));
    }

    @Test
    void takesTheArithmeticMeanUnlessBothNumbersArePositive() {
        assertThat(RadialGravity.logarithmicMean(0, 3)).isEqualTo(1.5);
        assertThat(RadialGravity.logarithmicMean(-1, 3)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, INNER_RADIUS / 10})
    void holdsItsValuesBelowTheSmallestRadius(double radius) {
        assertThat(gravity.circularSpeedSquared(radius)).isEqualTo(gravity.circularSpeedSquared(INNER_RADIUS / 2));
    }

    @ParameterizedTest
    @ValueSource(doubles = {OUTER_RADIUS * 10, Double.POSITIVE_INFINITY})
    void holdsItsValuesBeyondTheLargestRadius(double radius) {
        assertThat(gravity.escapeSpeedSquared(radius)).isEqualTo(gravity.escapeSpeedSquared(OUTER_RADIUS * 2));
    }
}
