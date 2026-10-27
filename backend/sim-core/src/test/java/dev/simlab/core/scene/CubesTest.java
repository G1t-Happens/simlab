package dev.simlab.core.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CubesTest {

    private static final Color GREEN = Color.rgb(0x3fb950);

    @Test
    void takesUpTheNumberOfCubesAndFourFloatsPerCube() {
        assertThat(new Cubes(4_096, "Cells", GREEN).frameFloats()).isEqualTo(1 + 4 * 4_096);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void needsRoomForOneCubeAtLeast(int capacity) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Cubes(capacity, "Cells", GREEN));
    }

    @Test
    void needsALabel() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Cubes(1, " ", GREEN))
                .withMessage("Cubes need a label");
    }
}
