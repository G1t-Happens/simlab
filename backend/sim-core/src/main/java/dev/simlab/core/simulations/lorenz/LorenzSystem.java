package dev.simlab.core.simulations.lorenz;

import dev.simlab.core.InvalidInputException;
import dev.simlab.core.toolkit.ode.OdeSystem;

/**
 * The Lorenz equations, evaluated for any number of independent particles at once.
 *
 * <p>The state holds x, y and z of one particle after another
 */
final class LorenzSystem implements OdeSystem {

    private double sigma;
    private double rho;
    private double beta;

    LorenzSystem(double sigma, double rho, double beta) {
        this.sigma = sigma;
        this.rho = rho;
        this.beta = beta;
    }

    @Override
    public void derivative(double t, double[] y, double[] dydt) {
        for (int i = 0; i < y.length; i += 3) {
            double px = y[i];
            double py = y[i + 1];
            double pz = y[i + 2];
            dydt[i] = sigma * (py - px);
            dydt[i + 1] = px * (rho - pz) - py;
            dydt[i + 2] = px * py - beta * pz;
        }
    }

    void setParameter(String name, double value) {
        switch (name) {
            case LorenzDefinition.SIGMA -> sigma = value;
            case LorenzDefinition.RHO -> rho = value;
            case LorenzDefinition.BETA -> beta = value;
            default -> throw new InvalidInputException("Unknown parameter '%s'".formatted(name));
        }
    }
}
