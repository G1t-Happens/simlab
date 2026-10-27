package dev.simlab.core.toolkit.ode;

/**
 * Euler's method, the simplest first-order method: it follows the derivative at the start of a step for the
 * whole step.
 *
 * <p>Steps don't allocate, the derivative lives in a buffer sized for one dimension of state. So instances
 * aren't thread-safe
 */
public final class Euler implements Integrator {

    private final double[] dydt;

    public Euler(int dimension) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("Dimension must be positive, but was " + dimension);
        }
        dydt = new double[dimension];
    }

    /**
     * Advances the state {@code y} in place from time {@code t} to {@code t + h}, along the derivative at the
     * start of the step
     */
    @Override
    public void step(OdeSystem system, double t, double[] y, double h) {
        if (y.length != dydt.length) {
            throw new IllegalArgumentException(
                    "Expected a state of dimension %d, but got %d".formatted(dydt.length, y.length));
        }
        system.derivative(t, y, dydt);
        for (int i = 0; i < y.length; i++) {
            y[i] += h * dydt[i];
        }
    }
}
