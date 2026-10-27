package dev.simlab.core.toolkit.ode;

/**
 * The classic fourth-order Runge-Kutta method: evaluates the derivative four times per step (at its start,
 * twice halfway and at its end) and follows their weighted mean for the whole step.
 *
 * <p>Steps don't allocate, the intermediate stages live in buffers sized for one dimension of state. So
 * instances aren't thread-safe
 */
public final class RungeKutta4 implements Integrator {

    private final double[] k1;
    private final double[] k2;
    private final double[] k3;
    private final double[] k4;
    private final double[] stage;

    public RungeKutta4(int dimension) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("Dimension must be positive, but was " + dimension);
        }
        k1 = new double[dimension];
        k2 = new double[dimension];
        k3 = new double[dimension];
        k4 = new double[dimension];
        stage = new double[dimension];
    }

    /**
     * Advances the state {@code y} in place from time {@code t} to {@code t + h}, along the mean of the four
     * derivatives weighted 1, 2, 2 and 1
     */
    @Override
    public void step(OdeSystem system, double t, double[] y, double h) {
        int dimension = stage.length;
        if (y.length != dimension) {
            throw new IllegalArgumentException(
                    "Expected a state of dimension %d, but got %d".formatted(dimension, y.length));
        }
        double halfStep = h / 2;

        system.derivative(t, y, k1);
        for (int i = 0; i < dimension; i++) {
            stage[i] = y[i] + halfStep * k1[i];
        }
        system.derivative(t + halfStep, stage, k2);
        for (int i = 0; i < dimension; i++) {
            stage[i] = y[i] + halfStep * k2[i];
        }
        system.derivative(t + halfStep, stage, k3);
        for (int i = 0; i < dimension; i++) {
            stage[i] = y[i] + h * k3[i];
        }
        system.derivative(t + h, stage, k4);

        double sixthStep = h / 6;
        for (int i = 0; i < dimension; i++) {
            y[i] += sixthStep * (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i]);
        }
    }
}
