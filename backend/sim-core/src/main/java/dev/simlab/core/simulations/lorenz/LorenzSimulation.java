package dev.simlab.core.simulations.lorenz;

import dev.simlab.core.Deselect;
import dev.simlab.core.InvalidInputException;
import dev.simlab.core.ParameterChange;
import dev.simlab.core.Select;
import dev.simlab.core.Simulation;
import dev.simlab.core.SimulationInput;
import dev.simlab.core.VertexInput;
import dev.simlab.core.toolkit.ode.Euler;
import dev.simlab.core.toolkit.ode.Integrator;
import dev.simlab.core.toolkit.ode.RungeKutta4;
import java.nio.FloatBuffer;

/**
 * Particles in the Lorenz system that start almost at the same point on the attractor.
 *
 * <p>Neighbors start {@value #INITIAL_SPACING} apart along x and chaos lets these tiny differences grow until
 * the particles spread over the whole attractor. Euler or classic Runge-Kutta moves them in steps whose size
 * can change while they fly. Every frame reports the state of a selected particle and its rates of change
 */
final class LorenzSimulation implements Simulation {

    /** Lorenz time units simulated per second of wall-clock time */
    static final double TIME_SCALE = 0.8;

    static final double INITIAL_SPACING = 1e-4;

    /**
     * Lorenz time a single particle is simulated for before the others join it. Starting from (1, 1, 1) it
     * first circles one fixed point, where differences between particles hardly grow
     */
    static final double WARM_UP_TIME = 20;

    /**
     * Step of the warm-up, small enough for classic Runge-Kutta to put the particles onto the attractor
     * accurately, whatever method and step the controls choose afterwards
     */
    static final double WARM_UP_STEP = 0.005;

    /** points along each axis of the grid where the x-ray shows the flow */
    static final int GRID_SIZE = 9;

    static final int ARROW_COUNT = GRID_SIZE * GRID_SIZE * GRID_SIZE;

    /** same for every arrow whatever the speed of the flow, its color shows that instead */
    static final double ARROW_LENGTH = 2.5;

    /** grid points around the attractor of the default parameters: x and y from -20 to 20, z from 5 to 45 */
    private static final double[] GRID = grid();

    private static final int NO_PARTICLE = -1;

    private final LorenzSystem system;
    private final Euler euler;
    private final RungeKutta4 rungeKutta4;
    private final double[] state;

    private final double[] gridRates = new double[GRID.length];

    private Integrator integrator;

    private double stepSize;

    /** time that passed but no step has covered yet */
    private double pendingTime;

    private double time;
    private int selected = NO_PARTICLE;

    /**
     * Creates particles on the attractor
     *
     * @param sigma the Prandtl number σ
     * @param rho the Rayleigh number ρ, relative to the one at which convection sets in
     * @param beta the factor β of the aspect ratio of the convection rolls
     * @param order chooses the method by its order: {@link LorenzDefinition#EULER} or {@link
     *     LorenzDefinition#RUNGE_KUTTA_4}
     * @param stepSize step size of the method in units of time, positive
     */
    LorenzSimulation(int particleCount, double sigma, double rho, double beta, double order, double stepSize) {
        if (!(stepSize > 0)) {
            throw new IllegalArgumentException("The step size must be positive, but was " + stepSize);
        }
        system = new LorenzSystem(sigma, rho, beta);
        double[] start = pointOnAttractor(system);
        state = new double[3 * particleCount];
        for (int particle = 0; particle < particleCount; particle++) {
            state[3 * particle] = start[0] + particle * INITIAL_SPACING;
            state[3 * particle + 1] = start[1];
            state[3 * particle + 2] = start[2];
        }
        euler = new Euler(state.length);
        rungeKutta4 = new RungeKutta4(state.length);
        integrator = integratorOfOrder(order);
        this.stepSize = stepSize;
    }

    private static double[] grid() {
        double[] grid = new double[3 * ARROW_COUNT];
        int i = 0;
        for (int column = 0; column < GRID_SIZE; column++) {
            for (int row = 0; row < GRID_SIZE; row++) {
                for (int layer = 0; layer < GRID_SIZE; layer++) {
                    grid[i++] = -20 + 5 * column;
                    grid[i++] = -20 + 5 * row;
                    grid[i++] = 5 + 5 * layer;
                }
            }
        }
        return grid;
    }

    /** Integrates a point from (1, 1, 1) for {@link #WARM_UP_TIME}, after that it lies on the attractor */
    private static double[] pointOnAttractor(LorenzSystem system) {
        double[] point = {1, 1, 1};
        var integrator = new RungeKutta4(point.length);
        long steps = Math.round(WARM_UP_TIME / WARM_UP_STEP);
        for (long i = 0; i < steps; i++) {
            integrator.step(system, i * WARM_UP_STEP, point, WARM_UP_STEP);
        }
        return point;
    }

    @Override
    public void apply(SimulationInput input) {
        switch (input) {
            case ParameterChange(String name, double value) -> setParameter(name, value);
            case Select(int particle) -> select(particle);
            case Deselect _ -> selected = NO_PARTICLE;
            case VertexInput _ ->
                throw new InvalidInputException("Particles of the Lorenz attractor cannot be grabbed");
        }
    }

    private void setParameter(String name, double value) {
        switch (name) {
            case LorenzDefinition.ORDER -> integrator = integratorOfOrder(value);
            case LorenzDefinition.STEP -> stepSize = value;
            default -> system.setParameter(name, value);
        }
    }

    /** Euler's method for order 1, the classic Runge-Kutta method for order 4 */
    private Integrator integratorOfOrder(double order) {
        if (order == LorenzDefinition.EULER) {
            return euler;
        }
        if (order == LorenzDefinition.RUNGE_KUTTA_4) {
            return rungeKutta4;
        }
        throw new InvalidInputException("There is no method of order " + order);
    }

    private void select(int particle) {
        if (particle < 0 || particle >= state.length / 3) {
            throw new InvalidInputException("There is no particle %d".formatted(particle));
        }
        selected = particle;
    }

    /**
     * Advances the particles in steps of the chosen size, as many as fit into the time that passed. The rest
     * carries over to the next call, so steps longer than a frame move the particles only every few frames
     */
    @Override
    public void step(double dt) {
        if (!(dt >= 0)) {
            throw new IllegalArgumentException("Time span must not be negative, but was " + dt);
        }
        pendingTime += dt * TIME_SCALE;
        while (pendingTime >= stepSize) {
            integrator.step(system, time, state, stepSize);
            time += stepSize;
            pendingTime -= stepSize;
        }
    }

    @Override
    public double time() {
        return time;
    }

    /** Writes x, y and z of one particle after another, then the readouts */
    @Override
    public void writeFrame(FloatBuffer frame) {
        for (double coordinate : state) {
            frame.put((float) coordinate);
        }
        writeReadouts(frame);
    }

    /**
     * Writes the readouts: x, y and z of the selected particle, then their rates of change from the Lorenz
     * equations. NaN while no particle is selected
     */
    private void writeReadouts(FloatBuffer frame) {
        double[] particle = {Double.NaN, Double.NaN, Double.NaN};
        double[] rates = {Double.NaN, Double.NaN, Double.NaN};
        if (selected != NO_PARTICLE) {
            System.arraycopy(state, 3 * selected, particle, 0, particle.length);
            system.derivative(time, particle, rates);
        }
        for (double coordinate : particle) {
            frame.put((float) coordinate);
        }
        for (double rate : rates) {
            frame.put((float) rate);
        }
    }

    /**
     * Writes the x-ray as the scene of {@link LorenzDefinition} lays it out: at every point of the grid an arrow
     * in the direction the Lorenz equations carry a state from there, with the speed of the flow as its value
     */
    @Override
    public void writeXray(FloatBuffer frame) {
        system.derivative(time, GRID, gridRates);
        frame.put(ARROW_COUNT);
        for (int i = 0; i < GRID.length; i += 3) {
            double speed = Math.sqrt(gridRates[i] * gridRates[i]
                    + gridRates[i + 1] * gridRates[i + 1]
                    + gridRates[i + 2] * gridRates[i + 2]);
            // at a fixed point the flow stands still, so the arrow has no direction and shrinks to a point
            double scale = speed > 0 ? ARROW_LENGTH / speed : 0;
            frame.put((float) GRID[i]).put((float) GRID[i + 1]).put((float) GRID[i + 2]);
            frame.put((float) (GRID[i] + scale * gridRates[i]));
            frame.put((float) (GRID[i + 1] + scale * gridRates[i + 1]));
            frame.put((float) (GRID[i + 2] + scale * gridRates[i + 2]));
            frame.put((float) speed);
        }
    }
}
