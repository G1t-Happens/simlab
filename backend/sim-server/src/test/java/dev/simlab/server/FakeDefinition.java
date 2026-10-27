package dev.simlab.server;

import dev.simlab.core.ParameterSpec;
import dev.simlab.core.Simulation;
import dev.simlab.core.SimulationDefinition;
import dev.simlab.core.scene.Camera;
import dev.simlab.core.scene.Color;
import dev.simlab.core.scene.Cubes;
import dev.simlab.core.scene.PointCloud;
import dev.simlab.core.scene.Scene;
import dev.simlab.core.scene.SceneObject;
import dev.simlab.core.scene.Vector3;
import java.util.List;
import java.util.Map;

/**
 * Defines a simulation of points without parameters, which always creates the same simulation
 *
 * @param simulation what {@link #create(Map)} returns
 * @param points number of points whose coordinates the frames hold
 * @param xrayCubes most cubes the x-ray shows, 0 for no x-ray
 */
public record FakeDefinition(String type, Simulation simulation, int points, int xrayCubes)
        implements SimulationDefinition {

    private static final Color WHITE = Color.rgb(0xffffff);

    public FakeDefinition(String type, Simulation simulation, int points) {
        this(type, simulation, points, 0);
    }

    public FakeDefinition(Simulation simulation, int points) {
        this("fake", simulation, points);
    }

    @Override
    public String name() {
        return "Fake simulation";
    }

    @Override
    public String description() {
        return "Stands in for a real simulation.";
    }

    @Override
    public String timeUnit() {
        return "s";
    }

    @Override
    public List<ParameterSpec> parameters() {
        return List.of();
    }

    @Override
    public Scene scene() {
        var camera = new Camera(new Vector3(0, 0, 10), new Vector3(0, 0, 0), new Vector3(0, 1, 0), false);
        List<SceneObject> xray = xrayCubes > 0 ? List.of(new Cubes(xrayCubes, "Cubes", WHITE)) : List.of();
        return new Scene(camera, List.of(new PointCloud(points, WHITE, WHITE, 0, true, false)), xray);
    }

    @Override
    public Simulation create(Map<String, Double> values) {
        return simulation;
    }
}
