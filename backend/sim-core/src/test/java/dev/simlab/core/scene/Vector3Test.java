package dev.simlab.core.scene;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class Vector3Test {

    @ParameterizedTest
    @CsvSource({"NaN, 0, 0", "0, Infinity, 0", "0, 0, -Infinity"})
    void needsFiniteCoordinates(double x, double y, double z) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Vector3(x, y, z));
    }
}
