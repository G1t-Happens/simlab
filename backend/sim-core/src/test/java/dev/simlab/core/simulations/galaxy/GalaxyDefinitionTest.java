package dev.simlab.core.simulations.galaxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import dev.simlab.core.Grab;
import dev.simlab.core.InvalidInputException;
import dev.simlab.core.ParameterChange;
import dev.simlab.core.ParameterSpec.Effect;
import dev.simlab.core.ParameterSpec.Range;
import dev.simlab.core.Select;
import dev.simlab.core.Simulation;
import dev.simlab.core.scene.Cubes;
import dev.simlab.core.scene.PointCloud;
import dev.simlab.core.scene.Readouts;
import dev.simlab.core.scene.Readouts.Readout;
import dev.simlab.core.scene.Scene;
import dev.simlab.core.scene.Vector3;
import java.nio.FloatBuffer;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GalaxyDefinitionTest {

    private static final int DISK = 8_000;
    private static final int BULGE = 1_600;

    private final GalaxyDefinition definition = new GalaxyDefinition();

    @Test
    void identifiesTheGalaxyCollision() {
        assertThat(definition.type()).isEqualTo("galaxies");
        assertThat(definition.name()).isEqualTo("Galaxy collision");
        assertThat(definition.description()).startsWith("Two spiral galaxies of 9600 stars each");
        assertThat(definition.timeUnit()).isEqualTo("Myr");
    }

    @Test
    void offersThePaceAndTheOpeningAngleLiveAndTheEncounterWhenItStartsOver() {
        assertThat(definition.parameters())
                .containsExactly(
                        new Range("pace", "Δt", "Myr/s", 1, 40, 15, Effect.LIVE),
                        new Range("openingAngle", "θ", "", 0.4, 1.5, 0.7, Effect.LIVE),
                        new Range("ratio", "q", "", 0.2, 1, 1, Effect.RESTART),
                        new Range("pericenter", "rₚ", "kpc", 4, 30, 10, Effect.RESTART),
                        new Range("inclination", "i", "°", 0, 180, 60, Effect.RESTART));
    }

    @Test
    void showsTheGlowingSelectableStarsOfTwoDisksAndBulgesToACameraThatCirclesThem() {
        Scene scene = definition.scene();

        assertThat(scene.objects())
                .filteredOn(PointCloud.class::isInstance)
                .map(PointCloud.class::cast)
                .extracting(PointCloud::count, PointCloud::trailLength, PointCloud::glowing, PointCloud::selectable)
                .containsExactly(
                        tuple(DISK, 0, true, true),
                        tuple(BULGE, 0, true, true),
                        tuple(DISK, 0, true, true),
                        tuple(BULGE, 0, true, true));
        assertThat(scene.camera().up()).isEqualTo(new Vector3(0, 0, 1));
        assertThat(scene.camera().orbiting()).isTrue();
    }

    @Test
    void reportsWhatTheGravityOfTheStarsCostsAndHowExactItIsAfterTheStars() {
        assertThat(definition.scene().objects())
                .last()
                .isInstanceOfSatisfying(
                        Readouts.class,
                        readouts -> assertThat(readouts.readouts())
                                .extracting(Readout::name, Readout::label, Readout::unit)
                                .containsExactly(
                                        tuple("pairs", "N(N−1)/2", ""),
                                        tuple("interactions", "I", ""),
                                        tuple("starInteractions", "nᵢ", ""),
                                        tuple("starForceError", "δᵢ", "%"),
                                        tuple("energyError", "ΔE/E₀", "%")));
    }

    @Test
    void writesFramesThatFitItsScene() {
        FloatBuffer frame = FloatBuffer.allocate(definition.scene().frameFloats());

        definition.create(Map.of()).writeFrame(frame);

        assertThat(frame.hasRemaining()).isFalse();
    }

    @Test
    void showsTheCubesThatActOnTheSelectedStarInItsXray() {
        assertThat(definition.scene().xray())
                .singleElement()
                .isInstanceOfSatisfying(
                        Cubes.class, cubes -> assertThat(cubes.capacity()).isEqualTo(6_144));
    }

    @Test
    void writesXraysThatFitItsScene() {
        FloatBuffer xray = FloatBuffer.allocate(definition.scene().xrayFloats());

        definition.create(Map.of()).writeXray(xray);

        assertThat(xray.hasRemaining()).isFalse();
    }

    @Test
    void startsWithTheGivenValuesOfItsParameters() {
        Simulation fast = definition.create(Map.of("pace", 40.0, "inclination", 90.0));
        Simulation flat = definition.create(Map.of("inclination", 0.0));

        fast.step(0.1);

        assertThat(fast.time()).isCloseTo(4, within(1e-9));
        assertThat(heightOfTheSecondDisk(fast)).as("height of an upright disk").isGreaterThan(3);
        assertThat(heightOfTheSecondDisk(flat))
                .as("height of a disk in the plane of the orbit")
                .isLessThan(0.5);
    }

    @Test
    void createsGalaxiesWithStarsOfTheirOwn() {
        assertThat(frame(definition.create(Map.of()))).isNotEqualTo(frame(definition.create(Map.of())));
    }

    @Test
    void acceptsChangesOfThePaceAndTheOpeningAngle() {
        ParameterChange pace = new ParameterChange("pace", 40);
        ParameterChange openingAngle = new ParameterChange("openingAngle", 1.5);

        assertThat(definition.validate(pace)).isSameAs(pace);
        assertThat(definition.validate(openingAngle)).isSameAs(openingAngle);
    }

    @Test
    void rejectsChangesOfTheEncounterWhileTheGalaxiesCollide() {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> definition.validate(new ParameterChange("ratio", 0.5)))
                .withMessage("Parameter 'ratio' takes effect when the simulation starts over");
    }

    @Test
    void rejectsGrabbingAStar() {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> definition.validate(new Grab(0, 1, 2, 3)))
                .withMessage("Galaxy collision has no vertices to grab");
    }

    @Test
    void acceptsSelectingAStarOfEitherGalaxy() {
        var select = new Select(2 * (DISK + BULGE) - 1);

        assertThat(definition.validate(select)).isSameAs(select);
    }

    @Test
    void rejectsSelectingAStarThatDoesNotExist() {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> definition.validate(new Select(2 * (DISK + BULGE))))
                .withMessage("There is no point 19200 to select");
    }

    private float[] frame(Simulation simulation) {
        FloatBuffer frame = FloatBuffer.allocate(definition.scene().frameFloats());
        simulation.writeFrame(frame);
        return frame.array();
    }

    /** RMS height of the disk stars of the second galaxy */
    private double heightOfTheSecondDisk(Simulation simulation) {
        float[] frame = frame(simulation);
        int first = DISK + BULGE;
        return Math.sqrt(IntStream.range(first, first + DISK)
                .mapToDouble(star -> frame[3 * star + 2] * frame[3 * star + 2])
                .average()
                .orElseThrow());
    }
}
