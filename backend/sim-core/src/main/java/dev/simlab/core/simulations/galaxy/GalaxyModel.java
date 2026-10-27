package dev.simlab.core.simulations.galaxy;

import java.util.random.RandomGenerator;

/**
 * How the mass of a disk galaxy is distributed: a disk and a bulge of stars in a halo of dark matter.
 *
 * <p>Lengths in kpc and masses in units of 10<sup>10</sup> solar masses, so G = 1 with velocities in units of
 * 207.4 km/s
 *
 * @param disk in the plane z = 0
 */
record GalaxyModel(Disk disk, Spheroid bulge, Spheroid halo) {

    double mass() {
        return disk.mass + bulge.mass + halo.mass;
    }

    /**
     * A galaxy of the same density with its mass scaled by a factor: lengths and velocities scale by the cube
     * root of the factor, the periods of its orbits stay the same
     */
    GalaxyModel scaled(double factor) {
        double length = Math.cbrt(factor);
        return new GalaxyModel(
                new Disk(
                        disk.mass * factor, disk.scaleLength * length, disk.scaleHeight * length, disk.radius * length),
                new Spheroid(bulge.mass * factor, bulge.scaleLength * length, bulge.radius * length),
                new Spheroid(halo.mass * factor, halo.scaleLength * length, halo.radius * length));
    }

    /**
     * Exponential disk: the surface density falls off as e<sup>-R/h</sup> with the distance R from the axis,
     * the density as sech²(z/z₀) with the height z above the plane
     *
     * @param scaleLength the scale length h
     * @param scaleHeight the scale height z₀
     * @param radius distance from the axis where the disk ends
     */
    record Disk(double mass, double scaleLength, double scaleHeight, double radius) {

        double surfaceDensity(double distance) {
            if (distance > radius) {
                return 0;
            }
            // the disk holds the share of an infinite disk's mass that lies within its radius
            double share = 1 - (1 + radius / scaleLength) * Math.exp(-radius / scaleLength);
            return mass / share / (2 * Math.PI * scaleLength * scaleLength) * Math.exp(-distance / scaleLength);
        }

        /**
         * Draws a star's distance from the axis. In scale lengths the distances in an infinite disk follow a
         * gamma distribution of shape 2, i.e. the sum of two exponentially distributed numbers
         */
        double sampleDistance(RandomGenerator random) {
            double distance;
            do {
                distance = -scaleLength * Math.log((1 - random.nextDouble()) * (1 - random.nextDouble()));
            } while (distance > radius);
            return distance;
        }

        /** Draws a star's height above the plane by inverting the cumulative distribution */
        double sampleHeight(RandomGenerator random) {
            double share;
            do {
                share = random.nextDouble();
            } while (share == 0);
            return scaleHeight * 0.5 * Math.log(share / (1 - share));
        }
    }

    /**
     * Spherical mass distribution with the density profile of Hernquist (1990), ρ(r) ∝ 1 / (r (r + a)³), up
     * to a radius where it ends. Within the radius r the infinite profile holds the share (r / (r + a))² of
     * its mass, whose inverse draws the radii of particles
     *
     * @param mass mass within the radius where the profile ends
     * @param scaleLength the scale length a
     */
    record Spheroid(double mass, double scaleLength, double radius) {

        double density(double distance) {
            if (distance > radius) {
                return 0;
            }
            double sum = distance + scaleLength;
            return mass / share() * scaleLength / (2 * Math.PI * distance * sum * sum * sum);
        }

        double sampleDistance(RandomGenerator random) {
            double root = Math.sqrt(random.nextDouble() * share());
            return scaleLength * root / (1 - root);
        }

        /** share of the infinite profile's mass that lies within the radius */
        private double share() {
            double root = radius / (radius + scaleLength);
            return root * root;
        }
    }
}
