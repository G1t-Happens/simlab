package dev.simlab.core.toolkit.ode;

/** A system of first-order ordinary differential equations {@code y' = f(t, y)} */
@FunctionalInterface
public interface OdeSystem {

    /**
     * Computes the derivative {@code f(t, y)}
     *
     * @param y the state, must not be modified
     * @param dydt receives the derivative, same length as {@code y}
     */
    void derivative(double t, double[] y, double[] dydt);
}
