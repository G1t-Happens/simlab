package dev.simlab.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.simlab.core.ParameterSpec.Choice;
import dev.simlab.core.ParameterSpec.Choice.Option;
import dev.simlab.core.ParameterSpec.Effect;
import dev.simlab.core.ParameterSpec.Range;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ParameterSpecTest {

    private static final Option EULER = new Option(1, "Euler");
    private static final Option RUNGE_KUTTA = new Option(4, "Runge-Kutta 4");

    private final ParameterSpec range = new Range("rho", "ρ", "", 0, 60, 28, Effect.LIVE);
    private final ParameterSpec choice = new Choice("order", "p", List.of(EULER, RUNGE_KUTTA), 4, Effect.LIVE);

    @Test
    void findsItsValueAmongValuesByName() {
        assertThat(range.valueIn(Map.of("rho", 20.0, "sigma", 5.0))).isEqualTo(20);
        assertThat(choice.valueIn(Map.of("order", 1.0))).isEqualTo(1);
    }

    @Test
    void fallsBackToItsDefaultValueWithoutAValueOfItsOwn() {
        assertThat(range.valueIn(Map.of("sigma", 5.0))).isEqualTo(28);
        assertThat(choice.valueIn(Map.of())).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 28, 60})
    void acceptsValuesWithinTheRange(double value) {
        assertThat(range.accepts(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.001, 60.001, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsValuesOutsideTheRange(double value) {
        assertThat(range.accepts(value)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(doubles = {1, 4})
    void acceptsTheValuesOfTheOptions(double value) {
        assertThat(choice.accepts(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(doubles = {2, 3.99, Double.NaN})
    void rejectsValuesThatNoOptionHas(double value) {
        assertThat(choice.accepts(value)).isFalse();
    }

    @Test
    void requiresAName() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Range(" ", "ρ", "", 0, 60, 28, Effect.LIVE));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Choice("", "p", List.of(EULER, RUNGE_KUTTA), 4, Effect.LIVE));
    }

    @Test
    void requiresALabel() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Range("rho", "", "", 0, 60, 28, Effect.LIVE));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Choice("order", " ", List.of(EULER, RUNGE_KUTTA), 4, Effect.LIVE));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN})
    void requiresMaxAboveMin(double max) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Range("rho", "ρ", "", 0, max, 0, Effect.LIVE));
    }

    @Test
    void requiresAFiniteRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Range("rho", "ρ", "", 0, Double.POSITIVE_INFINITY, 28, Effect.LIVE));
    }

    @Test
    void requiresTheDefaultValueWithinTheRange() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Range("rho", "ρ", "", 0, 60, 61, Effect.LIVE));
    }

    @Test
    void requiresTwoOptionsAtLeast() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Choice("order", "p", List.of(EULER), 1, Effect.LIVE))
                .withMessage("'order' needs at least two options to choose from");
    }

    @Test
    void requiresOptionsOfValuesOfTheirOwn() {
        var alsoFirstOrder = new Option(1, "Forward Euler");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Choice("order", "p", List.of(EULER, RUNGE_KUTTA, alsoFirstOrder), 1, Effect.LIVE))
                .withMessage("'order' has more than one option of the value 1.0");
    }

    @Test
    void requiresTheDefaultValueAmongTheOptions() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Choice("order", "p", List.of(EULER, RUNGE_KUTTA), 2, Effect.LIVE))
                .withMessage("Default value of 'order' is no option");
    }

    @Test
    void keepsItsOptionsFromChanging() {
        List<Option> options = new ArrayList<>(List.of(EULER, RUNGE_KUTTA));
        var orders = new Choice("order", "p", options, 1, Effect.LIVE);

        options.clear();

        assertThat(orders.options()).containsExactly(EULER, RUNGE_KUTTA);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> orders.options().clear());
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.NEGATIVE_INFINITY})
    void requiresOptionsOfFiniteValues(double value) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Option(value, "Euler"));
    }

    @Test
    void requiresOptionsWithLabels() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Option(1, " "))
                .withMessage("An option needs a label");
    }
}
