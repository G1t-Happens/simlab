package dev.simlab.core.simulations.galaxy;

import dev.simlab.core.ParameterSpec;
import dev.simlab.core.ParameterSpec.Effect;
import dev.simlab.core.ParameterSpec.Range;
import dev.simlab.core.Simulation;
import dev.simlab.core.SimulationDefinition;
import dev.simlab.core.scene.Camera;
import dev.simlab.core.scene.Color;
import dev.simlab.core.scene.Cubes;
import dev.simlab.core.scene.PointCloud;
import dev.simlab.core.scene.Readouts;
import dev.simlab.core.scene.Readouts.Readout;
import dev.simlab.core.scene.Scene;
import dev.simlab.core.scene.Vector3;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Two disk galaxies that collide and merge. Pace and the opening angle of gravity can be changed live, the
 * encounter can be set up anew, and stars can be selected to read what their gravity costs
 */
public final class GalaxyDefinition implements SimulationDefinition {

    static final String PACE = "pace";
    static final String OPENING_ANGLE = "openingAngle";
    static final String RATIO = "ratio";
    static final String PERICENTER = "pericenter";
    static final String INCLINATION = "inclination";

    /** keeps the median error of gravity at about 0.3 % */
    static final double DEFAULT_OPENING_ANGLE = 0.7;

    /** particles of each galaxy, about 35,000 in all, which the server moves in real time */
    static final ParticleCounts COUNTS = new ParticleCounts(8_000, 1_600, 8_000);

    private static final ParameterSpec PACE_SPEC = new Range(PACE, "Δt", "Myr/s", 1, 40, 15, Effect.LIVE);

    /**
     * From 0.4, where a step takes 2.7x as long as at the default but the force errs a quarter as much, to
     * 1.5, where the force errs 8x as much
     */
    private static final ParameterSpec OPENING_ANGLE_SPEC =
            new Range(OPENING_ANGLE, "θ", "", 0.4, 1.5, DEFAULT_OPENING_ANGLE, Effect.LIVE);

    private static final ParameterSpec RATIO_SPEC = new Range(RATIO, "q", "", 0.2, 1, 1, Effect.RESTART);
    private static final ParameterSpec PERICENTER_SPEC = new Range(PERICENTER, "rₚ", "kpc", 4, 30, 10, Effect.RESTART);
    private static final ParameterSpec INCLINATION_SPEC = new Range(INCLINATION, "i", "°", 0, 180, 60, Effect.RESTART);
    private static final List<ParameterSpec> PARAMETERS =
            List.of(PACE_SPEC, OPENING_ANGLE_SPEC, RATIO_SPEC, PERICENTER_SPEC, INCLINATION_SPEC);

    /**
     * Pairs of particles a direct sum of their gravity computes, the interactions the tree computes instead,
     * those of the selected star and the relative error of its acceleration, and the change of the total
     * energy since the start, in the order the simulation writes them
     */
    private static final Readouts READOUTS = new Readouts(List.of(
            new Readout("pairs", "N(N−1)/2", ""),
            new Readout("interactions", "I", ""),
            new Readout("starInteractions", "nᵢ", ""),
            new Readout("starForceError", "δᵢ", "%"),
            new Readout("energyError", "ΔE/E₀", "%")));

    /**
     * Room for the cubes acting on the selected star as a whole: 1.5x the most that do at the smallest opening
     * angle of the controls, about 4,000 on a star in the dense remnant of the merger
     */
    static final int CUBE_CAPACITY = 6_144;

    private static final Cubes XRAY =
            new Cubes(CUBE_CAPACITY, "Cubes of the tree that act on the selected star as a whole", Color.rgb(0x3fb950));

    /**
     * Disk and bulge stars of the first galaxy, then of the second one, numbered as in the simulation, then the
     * readouts, and the cubes of the tree as the x-ray. The disks glow from white at their centers to blue and
     * violet at their edges, the bulges in the warm light of old stars. The camera looks down at the orbit
     * from 130 kpc away and circles the center of mass
     */
    private static final Scene SCENE = new Scene(
            new Camera(new Vector3(0, -110, 70), new Vector3(0, 0, 0), new Vector3(0, 0, 1), true),
            List.of(
                    new PointCloud(COUNTS.disk(), Color.rgb(0xe3ebff), Color.rgb(0x4a78ff), 0, true, true),
                    new PointCloud(COUNTS.bulge(), Color.rgb(0xffe8b8), Color.rgb(0xffb766), 0, true, true),
                    new PointCloud(COUNTS.disk(), Color.rgb(0xf3e8ff), Color.rgb(0x9a5cff), 0, true, true),
                    new PointCloud(COUNTS.bulge(), Color.rgb(0xffe8b8), Color.rgb(0xffb766), 0, true, true),
                    READOUTS),
            List.of(XRAY));

    @Override
    public String type() {
        return "galaxies";
    }

    @Override
    public String name() {
        return "Galaxy collision";
    }

    @Override
    public String description() {
        return ("Two spiral galaxies of %d stars each, in halos of dark matter, fall towards each other. Their"
                        + " tides tear long tails out of the disks, until the galaxies merge into one. Slow down"
                        + " time as they meet, set up another encounter, or click a star to count what its gravity"
                        + " costs.")
                .formatted(COUNTS.stars());
    }

    @Override
    public String timeUnit() {
        return "Myr";
    }

    @Override
    public List<ParameterSpec> parameters() {
        return PARAMETERS;
    }

    @Override
    public Scene scene() {
        return SCENE;
    }

    /** Creates galaxies whose stars start at places of their own */
    @Override
    public Simulation create(Map<String, Double> values) {
        return new GalaxySimulation(
                COUNTS,
                ThreadLocalRandom.current().nextLong(),
                PACE_SPEC.valueIn(values),
                RATIO_SPEC.valueIn(values),
                PERICENTER_SPEC.valueIn(values),
                INCLINATION_SPEC.valueIn(values),
                OPENING_ANGLE_SPEC.valueIn(values));
    }
}
