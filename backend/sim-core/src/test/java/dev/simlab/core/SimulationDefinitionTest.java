package dev.simlab.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.simlab.core.ParameterSpec.Choice;
import dev.simlab.core.ParameterSpec.Choice.Option;
import dev.simlab.core.ParameterSpec.Effect;
import dev.simlab.core.ParameterSpec.Range;
import dev.simlab.core.scene.Camera;
import dev.simlab.core.scene.Color;
import dev.simlab.core.scene.Material;
import dev.simlab.core.scene.Material.Finish;
import dev.simlab.core.scene.Mesh;
import dev.simlab.core.scene.PointCloud;
import dev.simlab.core.scene.Scene;
import dev.simlab.core.scene.SceneObject;
import dev.simlab.core.scene.Vector3;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class SimulationDefinitionTest {

    private static final Color WHITE = Color.rgb(0xffffff);

    private final SimulationDefinition definition = definitionShowing(triangle(true), points(true));

    @Test
    void acceptsChangesOfLiveParametersWithinTheirRange() {
        var change = new ParameterChange("speed", 2.5);

        assertThat(definition.validate(change)).isSameAs(change);
    }

    @Test
    void rejectsUnknownParameters() {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> definition.validate(new ParameterChange("mass", 1)))
                .withMessage("Unknown parameter 'mass'");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, 11, Double.NaN})
    void rejectsValuesOutOfRange(double value) {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> definition.validate(new ParameterChange("speed", value)))
                .withMessage("Parameter 'speed' must lie between 0.0 and 10.0");
    }

    @Test
    void acceptsChoicesOfOptions() {
        var change = new ParameterChange("corners", 4);

        assertThat(definition.validate(change)).isSameAs(change);
    }

    @Test
    void rejectsValuesThatNoOptionHas() {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> definition.validate(new ParameterChange("corners", 5)))
                .withMessage("Parameter 'corners' must be one of 3.0, 4.0");
    }

    @Test
    void rejectsChangesOfParametersThatTakeEffectWhenTheSimulationStartsOver() {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> definition.validate(new ParameterChange("size", 3)))
                .withMessage("Parameter 'size' takes effect when the simulation starts over");
    }

    @Test
    void acceptsValuesToStartWithOfEveryParameter() {
        Map<String, Double> values = Map.of("speed", 2.5, "size", 3.0);

        assertThat(definition.validateValues(values)).isSameAs(values);
    }

    static List<Map<String, Double>> valuesThatDoNotFit() {
        return List.of(Map.of("mass", 1.0), Map.of("size", 6.0), Map.of("speed", Double.NaN));
    }

    @ParameterizedTest
    @MethodSource("valuesThatDoNotFit")
    void rejectsValuesToStartWithOfUnknownParametersOrOutOfRange(Map<String, Double> values) {
        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> definition.validateValues(values));
    }

    static List<VertexInput> vertexInputs() {
        return List.of(new Grab(0, 1, 2, 3), new Drag(2, -100, 1e9, 0), new Release(2));
    }

    @ParameterizedTest
    @MethodSource("vertexInputs")
    void acceptsInputsForTheVerticesOfTheGrabbableMesh(VertexInput input) {
        assertThat(definition.validate(input)).isSameAs(input);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 3})
    void rejectsVerticesThatTheMeshDoesNotHave(int vertex) {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> definition.validate(new Release(vertex)))
                .withMessage("There is no vertex %d, the mesh has 3 vertices", vertex);
    }

    static List<VertexInput> inputsAtPositionsThatAreNotFinite() {
        return List.of(
                new Grab(1, Double.NaN, 0, 0),
                new Grab(1, 0, Double.POSITIVE_INFINITY, 0),
                new Grab(1, 0, 0, Double.NEGATIVE_INFINITY),
                new Drag(1, 0, 0, Double.NaN));
    }

    @ParameterizedTest
    @MethodSource("inputsAtPositionsThatAreNotFinite")
    void rejectsPositionsThatAreNotFinite(VertexInput input) {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> definition.validate(input))
                .withMessage("The position of vertex 1 must be finite");
    }

    @Test
    void rejectsVertexInputsWithoutAGrabbableMesh() {
        SimulationDefinition withoutGrabbableMesh = definitionShowing(triangle(false));

        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> withoutGrabbableMesh.validate(new Release(0)))
                .withMessage("Test simulation has no vertices to grab");
    }

    static List<Selection> selections() {
        return List.of(new Select(0), new Select(1), new Deselect());
    }

    @ParameterizedTest
    @MethodSource("selections")
    void acceptsSelectionsOfThePointsOfSelectablePointClouds(Selection selection) {
        assertThat(definition.validate(selection)).isSameAs(selection);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2})
    void rejectsSelectionsOfPointsThatTheSceneDoesNotHave(int point) {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> definition.validate(new Select(point)))
                .withMessage("There is no point %d to select", point);
    }

    @Test
    void rejectsSelectionsWithoutSelectablePoints() {
        SimulationDefinition withoutSelectablePoints = definitionShowing(triangle(true), points(false));

        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> withoutSelectablePoints.validate(new Deselect()))
                .withMessage("Test simulation has no points to select");
    }

    private static Mesh triangle(boolean grabbable) {
        return new Mesh(3, List.of(0, 1, 2), new Material(WHITE, Finish.MATTE), grabbable);
    }

    private static PointCloud points(boolean selectable) {
        return new PointCloud(2, WHITE, WHITE, 0, false, selectable);
    }

    /**
     * Definition showing the objects, with a live parameter {@code speed} from 0 to 10 m/s, a parameter
     * {@code size} from 1 to 5 m that takes effect on restart, and a live choice of the {@code corners} of a
     * shape: 3 for a triangle or 4 for a square
     */
    private static SimulationDefinition definitionShowing(SceneObject... objects) {
        var camera = new Camera(new Vector3(0, 0, 5), new Vector3(0, 0, 0), new Vector3(0, 1, 0), false);
        var scene = new Scene(camera, List.of(objects));
        return new SimulationDefinition() {

            @Override
            public String type() {
                return "test";
            }

            @Override
            public String name() {
                return "Test simulation";
            }

            @Override
            public String description() {
                return "Tests the defaults of definitions.";
            }

            @Override
            public String timeUnit() {
                return "s";
            }

            @Override
            public List<ParameterSpec> parameters() {
                return List.of(
                        new Range("speed", "v", "m/s", 0, 10, 1, Effect.LIVE),
                        new Range("size", "l", "m", 1, 5, 2, Effect.RESTART),
                        new Choice(
                                "corners",
                                "n",
                                List.of(new Option(3, "Triangle"), new Option(4, "Square")),
                                3,
                                Effect.LIVE));
            }

            @Override
            public Scene scene() {
                return scene;
            }

            @Override
            public Simulation create(Map<String, Double> values) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
