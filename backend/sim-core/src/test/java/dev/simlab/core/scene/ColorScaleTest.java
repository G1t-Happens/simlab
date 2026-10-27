package dev.simlab.core.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ColorScaleTest {

    private static final Color BLUE = Color.rgb(0x58a6ff);
    private static final Color RED = Color.rgb(0xf85149);

    @Test
    void needsALabel() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ColorScale(" ", "%", -2, 2, List.of(BLUE, RED)))
                .withMessage("A color scale needs a label");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-2, -3, Double.NaN, Double.POSITIVE_INFINITY})
    void needsAMaxAboveTheMin(double max) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ColorScale("ε", "%", -2, max, List.of(BLUE, RED)));
    }

    @Test
    void needsTwoColorsAtLeast() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ColorScale("ε", "%", -2, 2, List.of(BLUE)))
                .withMessage("A color scale needs at least two colors, but got 1");
    }

    @Test
    void keepsItsColorsFromChanging() {
        List<Color> colors = new ArrayList<>(List.of(BLUE, RED));
        var scale = new ColorScale("ε", "%", -2, 2, colors);

        colors.clear();

        assertThat(scale.colors()).containsExactly(BLUE, RED);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> scale.colors().clear());
    }
}
