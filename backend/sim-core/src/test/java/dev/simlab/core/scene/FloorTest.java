package dev.simlab.core.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FloorTest {

    @Test
    void takesUpNoFloatsInFrames() {
        assertThat(new Floor(4).frameFloats()).isZero();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
    void needsAPositiveFiniteRadius(double radius) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Floor(radius));
    }
}
