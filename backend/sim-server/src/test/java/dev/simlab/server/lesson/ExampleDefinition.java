package dev.simlab.server.lesson;

import dev.simlab.core.ParameterSpec;
import dev.simlab.core.ParameterSpec.Effect;
import dev.simlab.core.ParameterSpec.Range;
import dev.simlab.core.Simulation;
import dev.simlab.core.SimulationDefinition;
import dev.simlab.core.scene.Camera;
import dev.simlab.core.scene.Readouts;
import dev.simlab.core.scene.Readouts.Readout;
import dev.simlab.core.scene.Scene;
import dev.simlab.core.scene.Vector3;
import java.util.List;
import java.util.Map;

/**
 * A simulation in the package of the methods among the test resources, so their code belongs to it: an amount
 * that decays at a rate. The rate changes live, the amount takes effect on restart and every frame reports
 * what remains of it
 */
final class ExampleDefinition implements SimulationDefinition {

    /** lessons need nothing of the scene but its readouts */
    private static final Scene SCENE = new Scene(
            new Camera(new Vector3(0, 0, 1), new Vector3(0, 0, 0), new Vector3(0, 1, 0), false),
            List.of(new Readouts(List.of(new Readout("remaining", "A(t)", "")))));

    @Override
    public String type() {
        return "example";
    }

    @Override
    public String name() {
        return "Example";
    }

    @Override
    public String description() {
        return "An amount decays at a rate.";
    }

    @Override
    public String timeUnit() {
        return "s";
    }

    @Override
    public List<ParameterSpec> parameters() {
        return List.of(
                new Range("rate", "λ", "1/s", 0, 10, 1, Effect.LIVE),
                new Range("amount", "A", "", 0, 100, 10, Effect.RESTART));
    }

    @Override
    public Scene scene() {
        return SCENE;
    }

    /** lessons need no simulation */
    @Override
    public Simulation create(Map<String, Double> values) {
        throw new UnsupportedOperationException("The example simulation cannot run");
    }
}
