package dev.simlab.core.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.simlab.core.scene.Material.Finish;
import dev.simlab.core.scene.Readouts.Readout;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class SceneTest {

    private static final Camera CAMERA =
            new Camera(new Vector3(0, 0, 5), new Vector3(0, 0, 0), new Vector3(0, 1, 0), false);
    private static final Material GRAY = new Material(Color.rgb(0x808080), Finish.MATTE);
    private static final Readouts READOUTS = new Readouts(List.of(new Readout("x", "x", "m")));

    @Test
    void holdsTheFloatsOfAllItsObjectsInEveryFrame() {
        var scene =
                new Scene(CAMERA, List.of(points(10, true), triangle(false), new Sphere(GRAY), new Floor(1), READOUTS));

        assertThat(scene.frameFloats())
                .as("10 points, 3 vertices, a sphere and a readout")
                .isEqualTo(30 + 9 + 4 + 1);
    }

    @Test
    void holdsTheFloatsOfTheObjectsOfItsXrayInFramesThatShowIt() {
        var scene = new Scene(CAMERA, List.of(points(10, true)), List.of(new Cubes(8, "Cells", GRAY.color())));

        assertThat(scene.frameFloats()).as("10 points").isEqualTo(30);
        assertThat(scene.xrayFloats()).as("number of cubes and 8 cubes").isEqualTo(1 + 4 * 8);
    }

    @Test
    void offersNoXrayUnlessGivenOne() {
        var scene = new Scene(CAMERA, List.of(new Floor(1)));

        assertThat(scene.xray()).isEmpty();
        assertThat(scene.xrayFloats()).isZero();
    }

    static List<SceneObject> objectsAlwaysInView() {
        return List.of(READOUTS, triangle(true), points(3, true));
    }

    @ParameterizedTest
    @MethodSource("objectsAlwaysInView")
    void showsTheObjectsThatThePointerReachesAlwaysRatherThanInTheXray(SceneObject object) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Scene(CAMERA, List.of(new Floor(1)), List.of(object)))
                .withMessageContaining("The x-ray shows neither readouts nor objects");
    }

    @Test
    void findsTheGrabbableMesh() {
        Mesh grabbable = triangle(true);

        assertThat(new Scene(CAMERA, List.of(triangle(false), grabbable)).grabbableMesh())
                .containsSame(grabbable);
        assertThat(new Scene(CAMERA, List.of(triangle(false))).grabbableMesh()).isEmpty();
    }

    @Test
    void allowsOneGrabbableMeshAtMost() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Scene(CAMERA, List.of(triangle(true), triangle(true))))
                .withMessageContaining("at most one grabbable mesh");
    }

    @Test
    void findsTheReadouts() {
        assertThat(new Scene(CAMERA, List.of(new Floor(1), READOUTS)).readouts())
                .containsSame(READOUTS);
        assertThat(new Scene(CAMERA, List.of(new Floor(1))).readouts()).isEmpty();
    }

    @Test
    void allowsOneObjectOfReadoutsAtMost() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Scene(CAMERA, List.of(READOUTS, READOUTS)))
                .withMessageContaining("at most one object of readouts");
    }

    @ParameterizedTest
    @CsvSource({"-1, false", "0, false", "1, false", "2, true", "4, true", "5, false"})
    void numbersThePointsAcrossItsPointCloudsToTellTheSelectableOnes(int point, boolean selectable) {
        var scene = new Scene(CAMERA, List.of(points(2, false), triangle(false), points(3, true)));

        assertThat(scene.isSelectable(point)).isEqualTo(selectable);
    }

    @Test
    void hasSelectablePointsIfOneOfItsPointCloudsHas() {
        assertThat(new Scene(CAMERA, List.of(points(2, false), points(3, true))).hasSelectablePoints())
                .isTrue();
        assertThat(new Scene(CAMERA, List.of(points(2, false), triangle(true))).hasSelectablePoints())
                .isFalse();
    }

    @Test
    void keepsItsObjectsFromChanging() {
        List<SceneObject> objects = new ArrayList<>(List.of(new Floor(1)));
        List<SceneObject> xray = new ArrayList<>(List.of(new Cubes(1, "Cells", GRAY.color())));
        var scene = new Scene(CAMERA, objects, xray);

        objects.clear();
        xray.clear();

        assertThat(scene.objects()).hasSize(1);
        assertThat(scene.xray()).hasSize(1);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> scene.objects().clear());
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> scene.xray().clear());
    }

    private static PointCloud points(int count, boolean selectable) {
        return new PointCloud(count, GRAY.color(), GRAY.color(), 0, true, selectable);
    }

    private static Mesh triangle(boolean grabbable) {
        return new Mesh(3, List.of(0, 1, 2), GRAY, grabbable);
    }
}
