package dev.simlab.core.toolkit.noise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.DoubleSummaryStatistics;
import java.util.stream.DoubleStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SmoothNoiseTest {

    private static final double PERIOD = 2;

    @Test
    void staysBetweenMinusOneAndOneWhileCoveringMostOfThatRange() {
        var noise = new SmoothNoise(42, PERIOD);

        DoubleSummaryStatistics values = times().map(noise::valueAt).summaryStatistics();

        assertThat(values.getMin()).isBetween(-1.0, -0.8);
        assertThat(values.getMax()).isBetween(0.8, 1.0);
    }

    @Test
    void changesSmoothly() {
        var noise = new SmoothNoise(42, PERIOD);
        double dt = 1e-3;

        double steepest = times().map(time -> Math.abs(noise.valueAt(time + dt) - noise.valueAt(time)) / dt)
                .max()
                .orElseThrow();

        // the interpolating curve is steepest halfway between two random values, with a slope of 15/8 times
        // their difference per period, and the random values differ by 2 at most
        assertThat(steepest).isLessThanOrEqualTo(2 * 15.0 / 8 / PERIOD);
    }

    @Test
    void repeatsItselfForTheSameSeedOnly() {
        double[] first = times().map(new SmoothNoise(7, PERIOD)::valueAt).toArray();
        double[] again = times().map(new SmoothNoise(7, PERIOD)::valueAt).toArray();
        double[] other = times().map(new SmoothNoise(8, PERIOD)::valueAt).toArray();

        assertThat(again).containsExactly(first);
        assertThat(other).isNotEqualTo(first);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN})
    void requiresAPositivePeriod(double period) {
        assertThatIllegalArgumentException().isThrownBy(() -> new SmoothNoise(1, period));
    }

    private static DoubleStream times() {
        return DoubleStream.iterate(0, time -> time < 100 * PERIOD, time -> time + PERIOD / 10);
    }
}
