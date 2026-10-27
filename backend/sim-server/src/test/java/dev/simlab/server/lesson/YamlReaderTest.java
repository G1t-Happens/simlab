package dev.simlab.server.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class YamlReaderTest {

    record Point(String name, double x, List<Double> y) {}

    @Test
    void readsYamlIntoARecord() {
        assertThat(YamlReader.read("name: P\nx: 1\ny: [2.5, -1e-4]", Point.class))
                .isEqualTo(new Point("P", 1, List.of(2.5, -1e-4)));
    }

    @Test
    void keepsBackslashesInPlainAndFoldedText() {
        assertThat(YamlReader.read("name: $\\rho < 1$\nx: 0\ny: []", Point.class)
                        .name())
                .isEqualTo("$\\rho < 1$");
        assertThat(YamlReader.read("name: >-\n  $\\sigma$\n  and $\\beta$\nx: 0\ny: []", Point.class)
                        .name())
                .isEqualTo("$\\sigma$ and $\\beta$");
    }

    @Test
    void rejectsPropertiesThatTheRecordDoesNotHave() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> YamlReader.read("name: P\nx: 1\ny: []\nz: 3", Point.class))
                .withMessageContaining("\"z\"");
    }

    @Test
    void rejectsKeysThatAppearTwice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> YamlReader.read("name: P\nx: 1\nx: 2\ny: []", Point.class))
                .withMessageContaining("duplicate key x");
    }

    @Test
    void rejectsTextThatIsNoYaml() {
        assertThatIllegalArgumentException().isThrownBy(() -> YamlReader.read("name: [P", Point.class));
    }

    @Test
    void rejectsEmptyText() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> YamlReader.read("", Point.class))
                .withMessage("The YAML is empty");
    }
}
