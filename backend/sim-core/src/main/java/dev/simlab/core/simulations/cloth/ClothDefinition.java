package dev.simlab.core.simulations.cloth;

import dev.simlab.core.ParameterSpec;
import dev.simlab.core.ParameterSpec.Effect;
import dev.simlab.core.ParameterSpec.Range;
import dev.simlab.core.Simulation;
import dev.simlab.core.SimulationDefinition;
import dev.simlab.core.scene.Camera;
import dev.simlab.core.scene.Color;
import dev.simlab.core.scene.ColorScale;
import dev.simlab.core.scene.Floor;
import dev.simlab.core.scene.Lines;
import dev.simlab.core.scene.Material;
import dev.simlab.core.scene.Material.Finish;
import dev.simlab.core.scene.Mesh;
import dev.simlab.core.scene.Readouts;
import dev.simlab.core.scene.Readouts.Readout;
import dev.simlab.core.scene.Scene;
import dev.simlab.core.scene.Sphere;
import dev.simlab.core.scene.Vector3;
import dev.simlab.core.toolkit.pbd.ParticleGrid;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** A cloth in gusty wind. Wind and gravity can be changed live and its vertices can be grabbed */
public final class ClothDefinition implements SimulationDefinition {

    static final String WIND = "wind";
    static final String GUSTS = "gusts";
    static final String GRAVITY = "gravity";

    /** particles along each edge of the cloth */
    static final int SIZE = 48;

    private static final ParticleGrid GRID = new ParticleGrid(SIZE);

    private static final ParameterSpec WIND_SPEC = new Range(WIND, "v", "m/s", 0, 8, 2, Effect.LIVE);
    private static final ParameterSpec GUSTS_SPEC = new Range(GUSTS, "Δv", "m/s", 0, 4, 1, Effect.LIVE);
    private static final ParameterSpec GRAVITY_SPEC = new Range(GRAVITY, "g", "m/s²", 0, 20, 9.81, Effect.LIVE);
    private static final List<ParameterSpec> PARAMETERS = List.of(WIND_SPEC, GUSTS_SPEC, GRAVITY_SPEC);

    /**
     * The wind at the time of the frame: the noise of its gusts and the speed they give it, then the noise
     * that turns it and the angle it turns by, in the order the simulation writes them
     */
    private static final Readouts READOUTS = new Readouts(List.of(
            new Readout("gustNoise", "N₁", ""),
            new Readout("speed", "|w|", "m/s"),
            new Readout("turnNoise", "N₂", ""),
            new Readout("turn", "φ", "rad")));

    /**
     * The constraints along rows and columns, which keep their length but for what one pass per substep
     * leaves: blue where compressed, gray where they keep their length and red where stretched, from 2 % on
     */
    private static final Lines XRAY = new Lines(
            GRID.stretchConstraintCount(),
            "Strain of the constraints along rows and columns",
            new ColorScale("ε", "%", -2, 2, List.of(Color.rgb(0x58a6ff), Color.rgb(0x6e7681), Color.rgb(0xf85149))),
            false);

    /**
     * The cloth and the sphere above the floor, then the readouts, in the order of their floats in frames,
     * and the strain of the constraints as the x-ray. The cloth hangs from 2.5 m and blows towards +z where
     * the sphere is, so from the side the camera sees both the cloth billow and the sphere beneath it
     */
    private static final Scene SCENE = new Scene(
            new Camera(new Vector3(4.2, 2, 2.8), new Vector3(0, 1.2, 0.6), new Vector3(0, 1, 0), false),
            List.of(
                    new Mesh(
                            GRID.particleCount(),
                            GRID.triangles(),
                            new Material(Color.rgb(0xd9513c), Finish.FABRIC),
                            true),
                    new Sphere(new Material(Color.rgb(0xc9d1d9), Finish.GLOSSY)),
                    new Floor(4),
                    READOUTS),
            List.of(XRAY));

    @Override
    public String type() {
        return "cloth";
    }

    @Override
    public String name() {
        return "Cloth";
    }

    @Override
    public String description() {
        return ("A cloth of %d × %d particles hangs from two corners. Gusts of wind blow it against a sphere,"
                        + " while thousands of constraints keep it from stretching. Grab it and pull.")
                .formatted(SIZE, SIZE);
    }

    @Override
    public String timeUnit() {
        return "s";
    }

    @Override
    public List<ParameterSpec> parameters() {
        return PARAMETERS;
    }

    @Override
    public Scene scene() {
        return SCENE;
    }

    /** Creates a cloth whose gusts differ from those of every other one */
    @Override
    public Simulation create(Map<String, Double> values) {
        return new ClothSimulation(
                SIZE,
                ThreadLocalRandom.current().nextLong(),
                WIND_SPEC.valueIn(values),
                GUSTS_SPEC.valueIn(values),
                GRAVITY_SPEC.valueIn(values));
    }
}
