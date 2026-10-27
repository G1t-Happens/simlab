package dev.simlab.core.toolkit.nbody;

import java.util.SplittableRandom;

/**
 * Particles in clumps of different sizes like in a galaxy: a dense core, a flat disk and a wide halo, with a
 * total mass of about 1 and random velocities of about their orbital speed
 */
final class Cluster {

    final double[] position;
    final double[] velocity;
    final double[] mass;

    Cluster(int count, long seed) {
        var random = new SplittableRandom(seed);
        position = new double[3 * count];
        velocity = new double[3 * count];
        mass = new double[count];
        for (int particle = 0; particle < count; particle++) {
            double scale = switch (particle % 3) {
                case 0 -> 0.2;
                case 1 -> 2;
                default -> 10;
            };
            double flattening = particle % 3 == 1 ? 0.1 : 1;
            int i = 3 * particle;
            position[i] = scale * random.nextGaussian();
            position[i + 1] = scale * random.nextGaussian();
            position[i + 2] = scale * flattening * random.nextGaussian();
            for (int axis = 0; axis < 3; axis++) {
                velocity[i + axis] = 0.3 * random.nextGaussian() / Math.sqrt(scale);
            }
            mass[particle] = random.nextDouble(0.5, 1.5) / count;
        }
    }

    /** accelerations with the softened gravity of every pair of particles summed up */
    double[] directSum(double softening) {
        double[] acceleration = new double[position.length];
        for (int a = 0; a < mass.length; a++) {
            for (int b = 0; b < mass.length; b++) {
                int i = 3 * a;
                int j = 3 * b;
                double dx = position[j] - position[i];
                double dy = position[j + 1] - position[i + 1];
                double dz = position[j + 2] - position[i + 2];
                double inverse = 1 / Math.sqrt(dx * dx + dy * dy + dz * dz + softening * softening);
                double pull = mass[b] * inverse * inverse * inverse;
                acceleration[i] += pull * dx;
                acceleration[i + 1] += pull * dy;
                acceleration[i + 2] += pull * dz;
            }
        }
        return acceleration;
    }

    /** kinetic energy plus the softened potential energy of every pair of particles */
    double energy(double softening) {
        double kinetic = 0;
        for (int a = 0; a < mass.length; a++) {
            int i = 3 * a;
            kinetic += mass[a] * (square(velocity[i]) + square(velocity[i + 1]) + square(velocity[i + 2])) / 2;
        }
        return kinetic + potentialEnergy(softening);
    }

    double potentialEnergy(double softening) {
        double energy = 0;
        for (int a = 0; a < mass.length; a++) {
            int i = 3 * a;
            for (int b = a + 1; b < mass.length; b++) {
                int j = 3 * b;
                double distanceSquared = square(position[j] - position[i])
                        + square(position[j + 1] - position[i + 1])
                        + square(position[j + 2] - position[i + 2]);
                energy -= mass[a] * mass[b] / Math.sqrt(distanceSquared + softening * softening);
            }
        }
        return energy;
    }

    double[] momentum() {
        double[] momentum = new double[3];
        for (int particle = 0; particle < mass.length; particle++) {
            for (int axis = 0; axis < 3; axis++) {
                momentum[axis] += mass[particle] * velocity[3 * particle + axis];
            }
        }
        return momentum;
    }

    private static double square(double value) {
        return value * value;
    }
}
