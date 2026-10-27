package dev.simlab.core.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LinesTest {

    private static final ColorScale STRAIN =
            new ColorScale("ε", "%", -2, 2, List.of(Color.rgb(0x58a6ff), Color.rgb(0xf85149)));

    @Test
    void takesUpTheNumberOfLinesAndSevenFloatsPerLine() {
        assertThat(new Lines(100, "Springs", STRAIN, false).frameFloats()).isEqualTo(1 + 7 * 100);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void needsRoomForOneLineAtLeast(int capacity) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Lines(capacity, "Springs", STRAIN, false));
    }

    @Test
    void needsALabel() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Lines(1, "", STRAIN, true))
                .withMessage("Lines need a label");
    }
}
