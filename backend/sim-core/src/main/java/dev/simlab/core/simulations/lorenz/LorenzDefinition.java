package dev.simlab.core.simulations.lorenz;

import dev.simlab.core.ParameterSpec;
import dev.simlab.core.ParameterSpec.Choice;
import dev.simlab.core.ParameterSpec.Choice.Option;
import dev.simlab.core.ParameterSpec.Effect;
import dev.simlab.core.ParameterSpec.Range;
import dev.simlab.core.Simulation;
import dev.simlab.core.SimulationDefinition;
import dev.simlab.core.scene.Camera;
import dev.simlab.core.scene.Color;
import dev.simlab.core.scene.ColorScale;
import dev.simlab.core.scene.Lines;
import dev.simlab.core.scene.PointCloud;
import dev.simlab.core.scene.Readouts;
import dev.simlab.core.scene.Readouts.Readout;
import dev.simlab.core.scene.Scene;
import dev.simlab.core.scene.Vector3;
import java.util.List;
import java.util.Map;

/**
 * A cloud of particles in the Lorenz system. Its classic parameters and its numerical method can be changed
 * live, and particles can be selected to read their state
 */
public final class LorenzDefinition implements SimulationDefinition {

    static final String SIGMA = "sigma";
    static final String RHO = "rho";
    static final String BETA = "beta";
    static final String ORDER = "order";
    static final String STEP = "step";

    /** order of Euler's method, the value of {@value #ORDER} that chooses it */
    static final double EULER = 1;

    /** order of the classic Runge-Kutta method, the value of {@value #ORDER} that chooses it */
    static final double RUNGE_KUTTA_4 = 4;

    static final int PARTICLE_COUNT = 1_000;

    // the Lorenz equations are dimensionless
    private static final ParameterSpec SIGMA_SPEC = new Range(SIGMA, "σ", "", 0.1, 30, 10, Effect.LIVE);
    private static final ParameterSpec RHO_SPEC = new Range(RHO, "ρ", "", 0, 60, 28, Effect.LIVE);
    private static final ParameterSpec BETA_SPEC = new Range(BETA, "β", "", 0.1, 10, 8.0 / 3, Effect.LIVE);

    /** numerical method, chosen by its order */
    private static final ParameterSpec ORDER_SPEC = new Choice(
            ORDER,
            "p",
            List.of(new Option(EULER, "Euler"), new Option(RUNGE_KUTTA_4, "Runge-Kutta 4")),
            RUNGE_KUTTA_4,
            Effect.LIVE);

    /** step size of the numerical method, in units of time */
    private static final ParameterSpec STEP_SPEC = new Range(STEP, "h", "", 0.001, 0.2, 0.005, Effect.LIVE);

    private static final List<ParameterSpec> PARAMETERS =
            List.of(SIGMA_SPEC, RHO_SPEC, BETA_SPEC, ORDER_SPEC, STEP_SPEC);

    /** frames the trail of a particle spans, about a second */
    private static final int TRAIL_LENGTH = 32;

    /** state of the selected particle and its rates of change, in the order the simulation writes them */
    private static final Readouts READOUTS = new Readouts(List.of(
            new Readout("x", "x", ""),
            new Readout("y", "y", ""),
            new Readout("z", "z", ""),
            new Readout("xDot", "ẋ", ""),
            new Readout("yDot", "ẏ", ""),
            new Readout("zDot", "ż", "")));

    /**
     * The flow at the points of a grid as arrows, from dim blue where it's slow to white where it's fast
     * beyond a speed of 400, which a fifth of the points exceed
     */
    private static final Lines XRAY = new Lines(
            LorenzSimulation.ARROW_COUNT,
            "Direction of the flow at the points of a grid, colored by its speed",
            new ColorScale("‖f‖", "", 0, 400, List.of(Color.rgb(0x30445c), Color.rgb(0x6e8fb5), Color.rgb(0xe6edf3))),
            true);

    /**
     * Particles and readouts, the flow as the x-ray. The attractor stands upright along z with its center at
     * z = 25 for the default parameters. From about 90 units away it fills 3/4 of the height of the view
     */
    private static final Scene SCENE = new Scene(
            new Camera(new Vector3(40, -80, 34), new Vector3(0, 0, 25), new Vector3(0, 0, 1), true),
            List.of(
                    new PointCloud(PARTICLE_COUNT, Color.rgb(0x8bdff8), Color.rgb(0xf88bd6), TRAIL_LENGTH, false, true),
                    READOUTS),
            List.of(XRAY));

    @Override
    public String type() {
        return "lorenz";
    }

    @Override
    public String name() {
        return "Lorenz attractor";
    }

    @Override
    public String description() {
        return "A thousand particles start almost at the same point. Chaos lets their tiny differences grow"
                + " until they spread over the whole attractor. Change the parameters and watch the system respond,"
                + " or click a particle to read its state.";
    }

    @Override
    public String timeUnit() {
        return "";
    }

    @Override
    public List<ParameterSpec> parameters() {
        return PARAMETERS;
    }

    @Override
    public Scene scene() {
        return SCENE;
    }

    @Override
    public Simulation create(Map<String, Double> values) {
        return new LorenzSimulation(
                PARTICLE_COUNT,
                SIGMA_SPEC.valueIn(values),
                RHO_SPEC.valueIn(values),
                BETA_SPEC.valueIn(values),
                ORDER_SPEC.valueIn(values),
                STEP_SPEC.valueIn(values));
    }
}
