package dev.simlab.server.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReferenceTest {

    @Test
    void parsesAClassAndAMethod() {
        assertThat(Reference.parse("RungeKutta4#step")).isEqualTo(new Reference("RungeKutta4", "step", null));
    }

    @Test
    void parsesTheTypesOfTheParameters() {
        assertThat(Reference.parse("Grid#tethers(double [], int ...)"))
                .isEqualTo(new Reference("Grid", "tethers", List.of("double[]", "int...")));
        assertThat(Reference.parse("Grid#size()")).isEqualTo(new Reference("Grid", "size", List.of()));
    }

    @Test
    void writesItselfAsALessonDoes() {
        assertThat(Reference.parse("Grid#tethers(double[],int...)")).hasToString("Grid#tethers(double[], int...)");
        assertThat(Reference.parse("RungeKutta4#step")).hasToString("RungeKutta4#step");
    }

    @ParameterizedTest
    @ValueSource(strings = {"step", "RungeKutta4", "rungeKutta4#step", "RungeKutta4#Step", "RungeKutta4#step("})
    void rejectsTextThatIsNoReference(String text) {
        assertThatIllegalArgumentException().isThrownBy(() -> Reference.parse(text));
    }
}
