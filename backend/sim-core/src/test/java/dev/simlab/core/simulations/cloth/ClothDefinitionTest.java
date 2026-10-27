package dev.simlab.core.simulations.cloth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import dev.simlab.core.Drag;
import dev.simlab.core.Grab;
import dev.simlab.core.InvalidInputException;
import dev.simlab.core.ParameterChange;
import dev.simlab.core.ParameterSpec.Effect;
import dev.simlab.core.ParameterSpec.Range;
import dev.simlab.core.Release;
import dev.simlab.core.Select;
import dev.simlab.core.Simulation;
import dev.simlab.core.SimulationInput;
import dev.simlab.core.scene.ColorScale;
import dev.simlab.core.scene.Floor;
import dev.simlab.core.scene.Lines;
import dev.simlab.core.scene.Mesh;
import dev.simlab.core.scene.Readouts;
import dev.simlab.core.scene.Readouts.Readout;
import dev.simlab.core.scene.Scene;
import dev.simlab.core.scene.Sphere;
import dev.simlab.core.toolkit.pbd.ParticleGrid;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ClothDefinitionTest {

    private static final int VERTEX_COUNT = 48 * 48;

    private final ClothDefinition definition = new ClothDefinition();

    @Test
    void identifiesTheClothSimulation() {
        assertThat(definition.type()).isEqualTo("cloth");
        assertThat(definition.name()).isEqualTo("Cloth");
        assertThat(definition.description()).startsWith("A cloth of 48 × 48 particles hangs from two corners.");
        assertThat(definition.timeUnit()).isEqualTo("s");
    }

    @Test
    void offersWindGustsAndGravityInSiUnitsLive() {
        assertThat(definition.parameters())
                .containsExactly(
                        new Range("wind", "v", "m/s", 0, 8, 2, Effect.LIVE),
                        new Range("gusts", "Δv", "m/s", 0, 4, 1, Effect.LIVE),
                        new Range("gravity", "g", "m/s²", 0, 20, 9.81, Effect.LIVE));
    }

    @Test
    void startsWithTheGivenValuesOfItsParameters() {
        Simulation still = definition.create(Map.of("wind", 0.0, "gusts", 0.0, "gravity", 0.0));
        float[] start = vertices(still);

        still.step(1);

        assertThat(vertices(still))
                .as("cloth without wind and gravity, which stays where it is")
                .containsExactly(start, within(1e-6f));
    }

    @Test
    void showsAGrabbableClothOf48By48VerticesAndASphereAboveAFloor() {
        Scene scene = definition.scene();

        assertThat(scene.objects()).hasExactlyElementsOfTypes(Mesh.class, Sphere.class, Floor.class, Readouts.class);
        assertThat(scene.grabbableMesh()).hasValueSatisfying(cloth -> {
            assertThat(cloth.vertexCount()).isEqualTo(VERTEX_COUNT);
            assertThat(cloth.triangles()).isEqualTo(new ParticleGrid(48).triangles());
        });
        assertThat(scene.hasSelectablePoints()).isFalse();
    }

    @Test
    void reportsTheWindAtTheTimeOfTheFrame() {
        assertThat(definition.scene().readouts().orElseThrow().readouts())
                .extracting(Readout::name, Readout::label, Readout::unit)
                .containsExactly(
                        tuple("gustNoise", "N₁", ""),
                        tuple("speed", "|w|", "m/s"),
                        tuple("turnNoise", "N₂", ""),
                        tuple("turn", "φ", "rad"));
    }

    @Test
    void writesFramesThatFitItsScene() {
        FloatBuffer frame = FloatBuffer.allocate(definition.scene().frameFloats());

        definition.create(Map.of()).writeFrame(frame);

        assertThat(frame.hasRemaining()).isFalse();
    }

    @Test
    void showsTheStrainOfTheConstraintsAlongRowsAndColumnsInItsXray() {
        assertThat(definition.scene().xray()).singleElement().isInstanceOfSatisfying(Lines.class, constraints -> {
            assertThat(constraints.capacity()).isEqualTo(2 * 48 * 47);
            assertThat(constraints.directed()).isFalse();
            assertThat(constraints.scale())
                    .extracting(ColorScale::label, ColorScale::unit, ColorScale::min, ColorScale::max)
                    .containsExactly("ε", "%", -2.0, 2.0);
        });
    }

    @Test
    void writesXraysThatFitItsScene() {
        FloatBuffer xray = FloatBuffer.allocate(definition.scene().xrayFloats());

        definition.create(Map.of()).writeXray(xray);

        assertThat(xray.hasRemaining()).isFalse();
    }

    @Test
    void createsClothsWithGustsOfTheirOwn() {
        Simulation first = definition.create(Map.of());
        Simulation second = definition.create(Map.of());

        first.step(5);
        second.step(5);

        assertThat(frame(first)).isNotEqualTo(frame(second));
    }

    static List<SimulationInput> validInputs() {
        return List.of(
                new ParameterChange("wind", 8),
                new Grab(0, 1, 2, 3),
                new Drag(VERTEX_COUNT - 1, -100, 1e9, 0),
                new Release(VERTEX_COUNT - 1));
    }

    @ParameterizedTest
    @MethodSource("validInputs")
    void acceptsChangesOfItsParametersAndInputsForItsVertices(SimulationInput input) {
        assertThat(definition.validate(input)).isSameAs(input);
    }

    @Test
    void rejectsParameterValuesOutOfRange() {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> definition.validate(new ParameterChange("wind", 9)))
                .withMessage("Parameter 'wind' must lie between 0.0 and 8.0");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, VERTEX_COUNT})
    void rejectsVerticesThatDoNotExist(int vertex) {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> definition.validate(new Release(vertex)))
                .withMessage("There is no vertex %d, the mesh has %d vertices", vertex, VERTEX_COUNT);
    }

    @Test
    void rejectsSelectingAParticle() {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> definition.validate(new Select(0)))
                .withMessage("Cloth has no points to select");
    }

    private FloatBuffer frame(Simulation simulation) {
        FloatBuffer frame = FloatBuffer.allocate(definition.scene().frameFloats());
        simulation.writeFrame(frame);
        return frame.flip();
    }

    private float[] vertices(Simulation simulation) {
        return Arrays.copyOf(frame(simulation).array(), 3 * VERTEX_COUNT);
    }
}
