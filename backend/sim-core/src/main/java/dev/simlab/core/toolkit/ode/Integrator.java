package dev.simlab.core.toolkit.ode;

/** A method that advances a system of ordinary differential equations by steps of a given size */
public interface Integrator {

    /**
     * Advances the state {@code y} in place from time {@code t} to {@code t + h}
     *
     * @throws IllegalArgumentException if the state doesn't have the dimension of this integrator
     */
    void step(OdeSystem system, double t, double[] y, double h);
}
