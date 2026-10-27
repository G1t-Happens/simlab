package dev.simlab.core.simulations.galaxy;

import dev.simlab.core.simulations.galaxy.GalaxyModel.Spheroid;
import dev.simlab.core.toolkit.nbody.BarnesHut;
import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;

/**
 * Gravity of a galaxy as a function of the distance from its center, measured with test particles that feel
 * the softened gravity of all its particles but exert none: averaged over spheres around the center, and over
 * circles around the axis in the plane z = 0 of its disk.
 *
 * <p>Measured at radii spaced evenly on a log scale. In between, values are interpolated linearly in the
 * logarithm of the radius, beyond the smallest and the largest radius they're held constant
 */
final class RadialGravity {

    /** radii at which gravity is measured */
    private static final int RADII = 64;

    /** directions in which gravity is measured at every radius, both on a sphere and on a circle */
    private static final int DIRECTIONS = 32;

    /** opening angle of the measuring tree, more accurate than the simulation's */
    private static final double OPENING_ANGLE = 0.5;

    private final double innerRadius;
    private final double logStep;

    /** inward acceleration at every radius, averaged over a sphere */
    private final double[] onSpheres;

    private final double[] circularSpeedSquared;

    private final double[] epicyclicRatio;
    private final double[] potential;

    private RadialGravity(double innerRadius, double logStep, double[] onSpheres, double[] inPlane, double mass) {
        this.innerRadius = innerRadius;
        this.logStep = logStep;
        this.onSpheres = onSpheres;
        circularSpeedSquared = new double[RADII];
        Arrays.setAll(circularSpeedSquared, i -> radius(i) * inPlane[i]);
        epicyclicRatio = new double[RADII];
        for (int i = 0; i < RADII; i++) {
            // κ² = d(R² v_c²)/dR / R³, derivative taken between the neighboring radii
            int inner = Math.max(i - 1, 0);
            int outer = Math.min(i + 1, RADII - 1);
            double derivative = (angularMomentumSquared(outer) - angularMomentumSquared(inner))
                    / ((outer - inner) * logStep * radius(i));
            double kappaSquared = derivative / Math.pow(radius(i), 3);
            double omegaSquared = circularSpeedSquared[i] / square(radius(i));
            // rotation curves lie between rigid rotation and the fall-off around a point mass
            epicyclicRatio[i] = omegaSquared > 0 ? Math.clamp(kappaSquared / (4 * omegaSquared), 0.25, 1) : 1;
        }
        potential = new double[RADII];
        // beyond the largest radius the galaxy pulls like a point of its mass
        potential[RADII - 1] = -mass / radius(RADII - 1);
        for (int i = RADII - 2; i >= 0; i--) {
            // Φ(r) = Φ(r') - ∫ g dr from r to r', with g r as the integrand over ln r
            potential[i] = potential[i + 1] - integrate(onSpheres[i] * radius(i), onSpheres[i + 1] * radius(i + 1));
        }
    }

    /**
     * Measures the gravity of particles between two radii
     *
     * @param position three coordinates per particle, around the center of the galaxy
     */
    static RadialGravity measure(
            double[] position, double[] mass, double softening, double innerRadius, double outerRadius) {
        int count = mass.length;
        double logStep = Math.log(outerRadius / innerRadius) / (RADII - 1);
        // test particles come after the particles: first those on spheres, then those on circles
        int circles = count + RADII * DIRECTIONS;
        double[] probed = Arrays.copyOf(position, 3 * (circles + RADII * DIRECTIONS));
        double[] probedMass = Arrays.copyOf(mass, circles + RADII * DIRECTIONS);
        double goldenAngle = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < RADII; i++) {
            double radius = innerRadius * Math.exp(i * logStep);
            for (int direction = 0; direction < DIRECTIONS; direction++) {
                // directions spread evenly over the sphere, on a Fibonacci lattice
                double z = 1 - (2 * direction + 1.0) / DIRECTIONS;
                double across = Math.sqrt(1 - z * z);
                int onSphere = 3 * (count + i * DIRECTIONS + direction);
                probed[onSphere] = radius * across * Math.cos(direction * goldenAngle);
                probed[onSphere + 1] = radius * across * Math.sin(direction * goldenAngle);
                probed[onSphere + 2] = radius * z;
                double azimuth = 2 * Math.PI * direction / DIRECTIONS;
                int onCircle = 3 * (circles + i * DIRECTIONS + direction);
                probed[onCircle] = radius * Math.cos(azimuth);
                probed[onCircle + 1] = radius * Math.sin(azimuth);
            }
        }
        double[] acceleration = new double[probed.length];
        new BarnesHut(softening, OPENING_ANGLE).accelerate(probed, probedMass, acceleration);
        double[] onSpheres = new double[RADII];
        double[] inPlane = new double[RADII];
        for (int i = 0; i < RADII; i++) {
            for (int direction = 0; direction < DIRECTIONS; direction++) {
                onSpheres[i] += inward(probed, acceleration, count + i * DIRECTIONS + direction) / DIRECTIONS;
                inPlane[i] += inward(probed, acceleration, circles + i * DIRECTIONS + direction) / DIRECTIONS;
            }
        }
        return new RadialGravity(
                innerRadius, logStep, onSpheres, inPlane, Arrays.stream(mass).sum());
    }

    /** component of a particle's acceleration that points towards the origin */
    private static double inward(double[] position, double[] acceleration, int particle) {
        int i = 3 * particle;
        double x = position[i];
        double y = position[i + 1];
        double z = position[i + 2];
        return -(acceleration[i] * x + acceleration[i + 1] * y + acceleration[i + 2] * z)
                / Math.sqrt(x * x + y * y + z * z);
    }

    /** squared speed of a circular orbit in the plane, at a distance from the axis */
    double circularSpeedSquared(double distance) {
        return interpolate(circularSpeedSquared, distance);
    }

    /**
     * κ² / 4Ω² at a distance from the axis: the squared ratio of the epicycle frequency κ (the small
     * oscillations of a star around a circular orbit) to twice the angular velocity Ω of the circular orbit.
     * In the epicyclic approximation this is the ratio σ_φ² / σ_R² of the squared velocity dispersions of disk
     * stars
     */
    double epicyclicRatio(double distance) {
        return interpolate(epicyclicRatio, distance);
    }

    /** squared speed with which a particle escapes from a distance to the center, on average */
    double escapeSpeedSquared(double radius) {
        return -2 * interpolate(potential, radius);
    }

    /**
     * Squared velocity dispersion with which a spheroid without rotation and with random motions alike in all
     * directions stays in equilibrium in this gravity, as a function of the radius: the solution
     * σ²(r) = 1/ρ(r) ∫<sub>r</sub><sup>∞</sup> ρ g dr of the Jeans equation
     */
    DoubleUnaryOperator jeansDispersionSquared(Spheroid spheroid) {
        double[] dispersionSquared = new double[RADII];
        double integral = 0;
        // integrand over ln r, ρ g r, at the next radius outwards
        double outerIntegrand = 0;
        for (int i = RADII - 1; i >= 0; i--) {
            double density = spheroid.density(radius(i));
            double integrand = density * onSpheres[i] * radius(i);
            if (i < RADII - 1) {
                integral += integrate(integrand, outerIntegrand);
            }
            outerIntegrand = integrand;
            dispersionSquared[i] = density > 0 ? integral / density : 0;
        }
        return radius -> interpolate(dispersionSquared, radius);
    }

    /**
     * Integrates over ln r between neighboring radii, given the integrand at both. The logarithmic mean of the
     * two values is exact for integrands that are powers of the radius, as densities and gravity of galaxies
     * are between their scale lengths
     */
    private double integrate(double inner, double outer) {
        return logStep * logarithmicMean(inner, outer);
    }

    /**
     * Logarithmic mean (a - b) / ln(a / b) of two positive numbers, between their geometric and their
     * arithmetic mean. The arithmetic mean if either of them isn't positive
     */
    static double logarithmicMean(double a, double b) {
        if (!(a > 0 && b > 0)) {
            return (a + b) / 2;
        }
        // the quotient tends to 1 as the numbers approach each other, but is 0 / 0 when they're equal
        return a == b ? a : (a - b) / Math.log1p((a - b) / b);
    }

    /** R² v_c², the squared angular momentum of a circular orbit, at a radius of the grid */
    private double angularMomentumSquared(int i) {
        return square(radius(i)) * circularSpeedSquared[i];
    }

    private double radius(int i) {
        return innerRadius * Math.exp(i * logStep);
    }

    private double interpolate(double[] table, double radius) {
        double place = Math.log(radius / innerRadius) / logStep;
        if (!(place > 0)) {
            return table[0];
        }
        if (place >= RADII - 1) {
            return table[RADII - 1];
        }
        int i = (int) place;
        return table[i] + (table[i + 1] - table[i]) * (place - i);
    }

    private static double square(double value) {
        return value * value;
    }
}
