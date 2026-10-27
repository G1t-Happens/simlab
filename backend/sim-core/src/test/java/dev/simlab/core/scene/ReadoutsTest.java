package dev.simlab.core.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.simlab.core.scene.Readouts.Readout;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReadoutsTest {

    private static final Readout SPEED = new Readout("speed", "|w|", "m/s");
    private static final Readout TURN = new Readout("turn", "φ", "rad");

    @Test
    void takesUpAFloatPerReadout() {
        assertThat(new Readouts(List.of(SPEED, TURN)).frameFloats()).isEqualTo(2);
    }

    @Test
    void needsAtLeastOneReadout() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Readouts(List.of()))
                .withMessage("Readouts need at least one readout");
    }

    @Test
    void rejectsReadoutsOfTheSameName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Readouts(List.of(SPEED, new Readout("speed", "v", "m/s"))))
                .withMessage("There is more than one readout named 'speed'");
    }

    @ParameterizedTest
    @CsvSource({"'', v", "speed, ' '"})
    void rejectsBlankNamesAndLabels(String name, String label) {
        assertThatIllegalArgumentException().isThrownBy(() -> new Readout(name, label, "m/s"));
    }

    @Test
    void keepsItsReadoutsFromChanging() {
        List<Readout> list = new ArrayList<>(List.of(SPEED));
        var readouts = new Readouts(list);

        list.add(TURN);

        assertThat(readouts.readouts()).containsExactly(SPEED);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> readouts.readouts().clear());
    }
}
