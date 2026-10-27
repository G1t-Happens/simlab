package dev.simlab.core.simulations.lorenz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import dev.simlab.core.Grab;
import dev.simlab.core.InvalidInputException;
import dev.simlab.core.ParameterSpec.Choice;
import dev.simlab.core.ParameterSpec.Choice.Option;
import dev.simlab.core.ParameterSpec.Effect;
import dev.simlab.core.ParameterSpec.Range;
import dev.simlab.core.Select;
import dev.simlab.core.Simulation;
import dev.simlab.core.scene.ColorScale;
import dev.simlab.core.scene.Lines;
import dev.simlab.core.scene.PointCloud;
import dev.simlab.core.scene.Readouts;
import dev.simlab.core.scene.Readouts.Readout;
import dev.simlab.core.scene.Scene;
import dev.simlab.core.scene.Vector3;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LorenzDefinitionTest {

    private final LorenzDefinition definition = new LorenzDefinition();

    @Test
    void identifiesTheLorenzSimulation() {
        assertThat(definition.type()).isEqualTo("lorenz");
        assertThat(definition.name()).isEqualTo("Lorenz attractor");
        assertThat(definition.description()).startsWith("A thousand particles start almost at the same point.");
        assertThat(definition.timeUnit()).as("unit of the dimensionless time").isEmpty();
    }

    @Test
    void offersTheClassicParametersWithTheirClassicDefaultsAndTheNumericalMethodLive() {
        assertThat(definition.parameters())
                .containsExactly(
                        new Range("sigma", "σ", "", 0.1, 30, 10, Effect.LIVE),
                        new Range("rho", "ρ", "", 0, 60, 28, Effect.LIVE),
                        new Range("beta", "β", "", 0.1, 10, 8.0 / 3, Effect.LIVE),
                        new Choice(
                                "order",
                                "p",
                                List.of(new Option(1, "Euler"), new Option(4, "Runge-Kutta 4")),
                                4,
                                Effect.LIVE),
                        new Range("step", "h", "", 0.001, 0.2, 0.005, Effect.LIVE));
    }

    @Test
    void startsWithTheGivenValuesOfItsParameters() {
        Simulation simulation = definition.create(Map.of("rho", 0.5));

        simulation.step(40);

        FloatBuffer frame = FloatBuffer.allocate(definition.scene().frameFloats());
        simulation.writeFrame(frame);
        assertThat(Arrays.copyOf(frame.array(), 3 * 1_000))
                .as("particles at rest at the origin, as for rho below 1")
                .usingComparatorWithPrecision(1e-3f)
                .containsOnly(0f);
    }

    @Test
    void showsAThousandSelectableParticlesWithTrailsToACameraThatCirclesTheUprightAttractor() {
        Scene scene = definition.scene();

        assertThat(scene.objects()).first().isInstanceOfSatisfying(PointCloud.class, particles -> {
            assertThat(particles.count()).isEqualTo(1_000);
            assertThat(particles.trailLength()).isPositive();
            assertThat(particles.selectable()).isTrue();
        });
        assertThat(scene.camera().up()).isEqualTo(new Vector3(0, 0, 1));
        assertThat(scene.camera().orbiting()).isTrue();
    }

    @Test
    void reportsTheStateOfTheSelectedParticleAndItsRatesOfChangeAfterTheParticles() {
        assertThat(definition.scene().objects())
                .last()
                .isInstanceOfSatisfying(
                        Readouts.class,
                        readouts -> assertThat(readouts.readouts())
                                .extracting(Readout::name, Readout::label, Readout::unit)
                                .containsExactly(
                                        tuple("x", "x", ""),
                                        tuple("y", "y", ""),
                                        tuple("z", "z", ""),
                                        tuple("xDot", "ẋ", ""),
                                        tuple("yDot", "ẏ", ""),
                                        tuple("zDot", "ż", "")));
    }

    @Test
    void writesFramesThatFitItsScene() {
        FloatBuffer frame = FloatBuffer.allocate(definition.scene().frameFloats());

        definition.create(Map.of()).writeFrame(frame);

        assertThat(frame.hasRemaining()).isFalse();
    }

    @Test
    void showsTheFlowAtThePointsOfAGridAsArrowsInItsXray() {
        assertThat(definition.scene().xray()).singleElement().isInstanceOfSatisfying(Lines.class, arrows -> {
            assertThat(arrows.capacity()).isEqualTo(9 * 9 * 9);
            assertThat(arrows.directed()).isTrue();
            assertThat(arrows.scale())
                    .extracting(ColorScale::label, ColorScale::min, ColorScale::max)
                    .containsExactly("‖f‖", 0.0, 400.0);
        });
    }

    @Test
    void writesXraysThatFitItsScene() {
        FloatBuffer xray = FloatBuffer.allocate(definition.scene().xrayFloats());

        definition.create(Map.of()).writeXray(xray);

        assertThat(xray.hasRemaining()).isFalse();
    }

    @Test
    void rejectsGrabbingAParticle() {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> definition.validate(new Grab(0, 1, 2, 3)))
                .withMessage("Lorenz attractor has no vertices to grab");
    }

    @Test
    void acceptsSelectingAParticle() {
        var select = new Select(999);

        assertThat(definition.validate(select)).isSameAs(select);
    }

    @Test
    void rejectsSelectingAParticleThatDoesNotExist() {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> definition.validate(new Select(1_000)))
                .withMessage("There is no point 1000 to select");
    }
}
