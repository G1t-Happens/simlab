package dev.simlab.core.simulations.galaxy;

import dev.simlab.core.simulations.galaxy.GalaxyModel.Disk;
import dev.simlab.core.simulations.galaxy.GalaxyModel.Spheroid;
import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.random.RandomGenerator;

/**
 * The particles of a disk galaxy, drawn from a {@link GalaxyModel} with velocities that keep the galaxy in
 * equilibrium: first the disk stars from the center outwards, then the bulge stars, then the dark matter of
 * the halo. The disk lies in the plane z = 0 and turns counterclockwise seen from above, the center of mass
 * rests at the origin.
 *
 * <p>Velocities follow Hernquist (1993). Disk stars circle the axis at about the speed of circular orbits,
 * with random motions that keep the disk as thick as its scale height and stable against clumping (as
 * Toomre's Q measures). Bulge and halo don't rotate, their random motions balance gravity as the Jeans
 * equation demands. Gravity is {@linkplain RadialGravity measured} from the particles themselves, softened
 * as in the simulation.
 *
 * <p>Particle {@code i} has the coordinates {@code 3i}, {@code 3i + 1} and {@code 3i + 2} of the position and
 * the velocity
 */
final class Galaxy {

    /** Toomre's Q of the disk at {@link #TOOMRE_RADIUS}, above 1 the disk is stable against clumping */
    private static final double TOOMRE_Q = 1.2;

    /** distance from the axis in disk scale lengths where the disk has {@link #TOOMRE_Q} */
    private static final double TOOMRE_RADIUS = 2.5;

    /**
     * Top speed of a bulge or halo particle as a share of the escape speed. The random motions are Gaussian,
     * and the tail would let some particles escape
     */
    private static final double MAX_SHARE_OF_ESCAPE_SPEED = 0.95;

    private final ParticleCounts counts;
    final double[] position;
    final double[] velocity;
    final double[] mass;

    private Galaxy(ParticleCounts counts) {
        this.counts = counts;
        position = new double[3 * counts.total()];
        velocity = new double[3 * counts.total()];
        mass = new double[counts.total()];
    }

    /**
     * Draws a galaxy from a model
     *
     * @param softening softening length of gravity in the simulation
     */
    static Galaxy sample(GalaxyModel model, ParticleCounts counts, double softening, RandomGenerator random) {
        var galaxy = new Galaxy(counts);
        galaxy.placeDisk(model.disk(), random);
        galaxy.placeSpheroid(counts.disk(), counts.bulge(), model.bulge(), random);
        galaxy.placeSpheroid(counts.stars(), counts.halo(), model.halo(), random);
        RadialGravity gravity = RadialGravity.measure(
                galaxy.position,
                galaxy.mass,
                softening,
                model.disk().scaleLength() / 100,
                1.5 * model.halo().radius());
        galaxy.setDiskVelocities(model.disk(), gravity, random);
        galaxy.setSpheroidVelocities(counts.disk(), counts.bulge(), model.bulge(), gravity, random);
        galaxy.setSpheroidVelocities(counts.stars(), counts.halo(), model.halo(), gravity, random);
        galaxy.moveToRestAtOrigin();
        return galaxy;
    }

    /**
     * Turns the galaxy about an axis through the origin, counterclockwise seen from where the axis points,
     * with Rodrigues' rotation formula
     *
     * @param angle in radians
     */
    void turn(double axisX, double axisY, double axisZ, double angle) {
        double length = Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
        double ux = axisX / length;
        double uy = axisY / length;
        double uz = axisZ / length;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        for (double[] vectors : List.of(position, velocity)) {
            for (int i = 0; i < vectors.length; i += 3) {
                double x = vectors[i];
                double y = vectors[i + 1];
                double z = vectors[i + 2];
                double along = (ux * x + uy * y + uz * z) * (1 - cos);
                vectors[i] = x * cos + (uy * z - uz * y) * sin + ux * along;
                vectors[i + 1] = y * cos + (uz * x - ux * z) * sin + uy * along;
                vectors[i + 2] = z * cos + (ux * y - uy * x) * sin + uz * along;
            }
        }
    }

    void move(double dx, double dy, double dz, double dvx, double dvy, double dvz) {
        for (int i = 0; i < position.length; i += 3) {
            position[i] += dx;
            position[i + 1] += dy;
            position[i + 2] += dz;
            velocity[i] += dvx;
            velocity[i + 1] += dvy;
            velocity[i + 2] += dvz;
        }
    }

    /** Places the disk stars at random, sorted by their distance from the axis */
    private void placeDisk(Disk disk, RandomGenerator random) {
        double[] distances = new double[counts.disk()];
        Arrays.setAll(distances, star -> disk.sampleDistance(random));
        Arrays.sort(distances);
        for (int star = 0; star < distances.length; star++) {
            double azimuth = 2 * Math.PI * random.nextDouble();
            position[3 * star] = distances[star] * Math.cos(azimuth);
            position[3 * star + 1] = distances[star] * Math.sin(azimuth);
            position[3 * star + 2] = disk.sampleHeight(random);
            mass[star] = disk.mass() / distances.length;
        }
    }

    private void placeSpheroid(int first, int count, Spheroid spheroid, RandomGenerator random) {
        for (int particle = first; particle < first + count; particle++) {
            double distance = spheroid.sampleDistance(random);
            double z = 2 * random.nextDouble() - 1;
            double azimuth = 2 * Math.PI * random.nextDouble();
            double across = Math.sqrt(1 - z * z);
            position[3 * particle] = distance * across * Math.cos(azimuth);
            position[3 * particle + 1] = distance * across * Math.sin(azimuth);
            position[3 * particle + 2] = distance * z;
            mass[particle] = spheroid.mass() / count;
        }
    }

    /**
     * Sets the velocities of the disk stars. The radial dispersion σ_R falls off as e^(-R/2h), with the square
     * root of the surface density, and gives the disk {@link #TOOMRE_Q}. The azimuthal dispersion follows from
     * the epicyclic approximation, the vertical one from the thickness of the disk, and the mean azimuthal
     * velocity lags behind the circular one by the asymmetric drift
     */
    private void setDiskVelocities(Disk disk, RadialGravity gravity, RandomGenerator random) {
        double h = disk.scaleLength();
        double toomreRadius = TOOMRE_RADIUS * h;
        // κ² = 4Ω² κ²/4Ω² with Ω² = v_c² / R²
        double kappa = Math.sqrt(4
                * gravity.circularSpeedSquared(toomreRadius)
                / square(toomreRadius)
                * gravity.epicyclicRatio(toomreRadius));
        // Q = σ_R κ / (3.36 G Σ) for a disk of stars
        double toomreDispersion = TOOMRE_Q * 3.36 * disk.surfaceDensity(toomreRadius) / kappa;
        for (int star = 0; star < counts.disk(); star++) {
            int i = 3 * star;
            double distance = Math.hypot(position[i], position[i + 1]);
            double radialSquared = square(toomreDispersion) * Math.exp((toomreRadius - distance) / h);
            double ratio = gravity.epicyclicRatio(distance);
            // v̄_φ² = v_c² + σ_R² (1 - σ_φ²/σ_R² + d ln(Σ σ_R²) / d ln R) where Σ σ_R² ∝ e^(-2R/h)
            double meanSquared =
                    gravity.circularSpeedSquared(distance) + radialSquared * (1 - ratio - 2 * distance / h);
            double radial = random.nextGaussian() * Math.sqrt(radialSquared);
            double azimuthal =
                    Math.sqrt(Math.max(meanSquared, 0)) + random.nextGaussian() * Math.sqrt(radialSquared * ratio);
            // away from the axis and counterclockwise around it, a star on the axis takes the x axis
            double azimuth = Math.atan2(position[i + 1], position[i]);
            double cos = Math.cos(azimuth);
            double sin = Math.sin(azimuth);
            velocity[i] = radial * cos - azimuthal * sin;
            velocity[i + 1] = radial * sin + azimuthal * cos;
            // σ_z² = π G Σ z₀ in an isothermal sheet of stars with the density profile sech²(z/z₀)
            velocity[i + 2] =
                    random.nextGaussian() * Math.sqrt(Math.PI * disk.surfaceDensity(distance) * disk.scaleHeight());
        }
    }

    /**
     * Sets the velocities of the given number of spheroid particles from the given one on: random in direction,
     * with the dispersion the Jeans equation demands, below the escape speed
     */
    private void setSpheroidVelocities(
            int first, int count, Spheroid spheroid, RadialGravity gravity, RandomGenerator random) {
        DoubleUnaryOperator dispersionSquared = gravity.jeansDispersionSquared(spheroid);
        for (int particle = first; particle < first + count; particle++) {
            int i = 3 * particle;
            double radius = Math.sqrt(square(position[i]) + square(position[i + 1]) + square(position[i + 2]));
            double dispersion = Math.sqrt(dispersionSquared.applyAsDouble(radius));
            double maxSpeedSquared = square(MAX_SHARE_OF_ESCAPE_SPEED) * gravity.escapeSpeedSquared(radius);
            do {
                velocity[i] = random.nextGaussian() * dispersion;
                velocity[i + 1] = random.nextGaussian() * dispersion;
                velocity[i + 2] = random.nextGaussian() * dispersion;
            } while (square(velocity[i]) + square(velocity[i + 1]) + square(velocity[i + 2]) > maxSpeedSquared);
        }
    }

    private void moveToRestAtOrigin() {
        double total = 0;
        double[] center = new double[6];
        for (int particle = 0; particle < mass.length; particle++) {
            total += mass[particle];
            for (int axis = 0; axis < 3; axis++) {
                center[axis] += mass[particle] * position[3 * particle + axis];
                center[3 + axis] += mass[particle] * velocity[3 * particle + axis];
            }
        }
        move(
                -center[0] / total,
                -center[1] / total,
                -center[2] / total,
                -center[3] / total,
                -center[4] / total,
                -center[5] / total);
    }

    private static double square(double value) {
        return value * value;
    }
}
