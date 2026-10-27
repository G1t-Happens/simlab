package dev.simlab.core.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ColorTest {

    @Test
    void splitsAHexadecimalValueIntoItsChannels() {
        assertThat(Color.rgb(0xd9513c)).isEqualTo(new Color(0xd9, 0x51, 0x3c));
        assertThat(Color.rgb(0xffffff)).isEqualTo(new Color(255, 255, 255));
    }

    @ParameterizedTest
    @ValueSource(ints = {0x1000000, -1})
    void rejectsValuesOfMoreThan24Bits(int value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Color.rgb(value))
                .withMessageEndingWith("is not a 24-bit RGB value");
    }

    @ParameterizedTest
    @CsvSource({"-1, 0, 0", "0, 256, 0", "0, 0, -1"})
    void rejectsChannelsOutOfRange(int red, int green, int blue) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Color(red, green, blue));
    }
}
