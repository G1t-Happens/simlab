package dev.simlab.core.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CameraTest {

    private static final Vector3 ORIGIN = new Vector3(0, 0, 0);

    static List<Arguments> upDirectionsAcrossTheLineOfSight() {
        return List.of(
                Arguments.of(new Vector3(0, 0, 5), new Vector3(0, 1, 0)),
                Arguments.of(new Vector3(0, 2, 4), new Vector3(0, 1, 0)),
                Arguments.of(new Vector3(5, 0, 0), new Vector3(0, 1, 0)),
                Arguments.of(new Vector3(0, 0, 5), new Vector3(1, 0, 0)),
                Arguments.of(new Vector3(3, 4, 5), new Vector3(1, 1, 1)));
    }

    @ParameterizedTest
    @MethodSource("upDirectionsAcrossTheLineOfSight")
    void looksAtItsTargetWithAnyUpDirectionAcrossTheLineOfSight(Vector3 position, Vector3 up) {
        var camera = new Camera(position, ORIGIN, up, true);

        assertThat(camera.up()).isEqualTo(up);
    }

    @Test
    void mustNotBeAtItsTarget() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Camera(ORIGIN, ORIGIN, new Vector3(0, 1, 0), false))
                .withMessage("The camera must not be at its target");
    }

    static List<Vector3> upDirectionsWithoutSideways() {
        return List.of(new Vector3(0, 0, 0), new Vector3(0, 0, 1), new Vector3(0, 0, -2));
    }

    @ParameterizedTest
    @MethodSource("upDirectionsWithoutSideways")
    void needsAnUpDirectionAcrossTheLineOfSight(Vector3 up) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Camera(new Vector3(0, 0, 5), ORIGIN, up, false))
                .withMessage("The up direction must neither be zero nor point along the line of sight");
    }
}
