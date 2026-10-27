package dev.simlab.core.simulations.galaxy;

import dev.simlab.core.Deselect;
import dev.simlab.core.InvalidInputException;
import dev.simlab.core.ParameterChange;
import dev.simlab.core.Select;
import dev.simlab.core.Simulation;
import dev.simlab.core.SimulationInput;
import dev.simlab.core.VertexInput;
import dev.simlab.core.scene.Cubes;
import dev.simlab.core.simulations.galaxy.GalaxyModel.Disk;
import dev.simlab.core.simulations.galaxy.GalaxyModel.Spheroid;
import dev.simlab.core.toolkit.nbody.BarnesHut;
import dev.simlab.core.toolkit.nbody.Leapfrog;
import java.nio.FloatBuffer;
import java.util.SplittableRandom;

/**
 * Two disk galaxies that fall towards each other on a parabolic orbit, tear each other apart with their tides
 * and merge.
 *
 * <p>Every particle attracts every other one. Gravity of the stars and the dark matter of both galaxies comes
 * from a {@linkplain BarnesHut Barnes-Hut tree} whose opening angle can change during the collision, and the
 * {@linkplain Leapfrog leapfrog method} moves the particles. As the galaxies pass through each other their
 * dark matter halos brake them by dynamical friction, until they fall back together and merge.
 *
 * <p>Every frame reports what the gravity costs and how exact it is: how many interactions the tree computed
 * in the last step, how many of them a selected star summed up and how far its acceleration deviates from the
 * exact sum, and how much the total energy (which the exact dynamics conserve) changed since the start.
 *
 * <p>Lengths in kpc and masses in units of 10<sup>10</sup> solar masses, so G = 1 with velocities in units of
 * 207.4 km/s and times in units of {@value #MYR_PER_TIME_UNIT} million years (Myr). The orbit lies in the
 * plane z = 0 and turns counterclockwise seen from above, around the center of mass of both galaxies at the
 * origin
 */
final class GalaxySimulation implements Simulation {

    static final double MYR_PER_TIME_UNIT = 4.715;

    /**
     * The first galaxy, somewhat smaller than the Milky Way: 5e10 solar masses of stars, four fifths of them
     * in a disk 36 kpc across, in a halo of 1.2e11 solar masses of dark matter. The halo is light enough for
     * the tides to tear long tails out of the disk
     */
    static final GalaxyModel MODEL =
            new GalaxyModel(new Disk(4, 3, 0.3, 18), new Spheroid(1, 0.6, 6), new Spheroid(12, 8, 40));

    /** softening length in kpc, about the distance between neighboring stars of a disk */
    static final double SOFTENING = 0.2;

    /** longest leapfrog step in units of time, with longer steps the energy error grows */
    static final double MAX_STEP = 0.16;

    /**
     * Opening angle of the tree that measures the energy. A tree of its own keeps the measurement apart from
     * the opening angle of the gravity (coarser trees would add their own error to it) and keeps the error of
     * the potential energy at about 0.01 %
     */
    static final double ENERGY_OPENING_ANGLE = 0.4;

    /**
     * Leapfrog steps from one energy measurement to the next. A measurement takes about as long as two steps
     * at the default opening angle, so it adds about 6 % to their time
     */
    static final int STEPS_PER_ENERGY = 30;

    /** distance between the centers of the galaxies at the start, in kpc */
    static final double START_DISTANCE = 60;

    /** angle in the orbital plane from the pericenter direction to the axis the second disk tilts about */
    private static final double TILT_AXIS_ANGLE = Math.toRadians(-30);

    private static final int NO_STAR = -1;

    private final BarnesHut gravity;
    private final Leapfrog leapfrog;

    /** measures the potential energy, see {@link #ENERGY_OPENING_ANGLE} */
    private final BarnesHut energyMeter = new BarnesHut(SOFTENING, ENERGY_OPENING_ANGLE);

    private final double[] cubes = new double[Cubes.FLOATS_PER_CUBE * GalaxyDefinition.CUBE_CAPACITY];

    /** stars come first among the particles, the dark matter follows */
    private final int starCount;

    private final double[] position;
    private final double[] velocity;
    private final double[] mass;

    private final double initialEnergy;

    /** total energy at the latest measurement */
    private double energy;

    private int stepsSinceEnergy;

    /** Myr simulated per second of wall-clock time */
    private double pace;

    /** in units of time */
    private double time;

    private int selected = NO_STAR;

    /**
     * Creates two galaxies that approach each other: the stars of the first galaxy and of the second one,
     * then the dark matter of both
     *
     * @param seed determines where the particles start
     * @param pace Myr simulated per second of wall-clock time
     * @param massRatio mass of the second galaxy relative to the first one, positive
     * @param pericenter closest distance between the galaxy centers in kpc if their masses were points, at
     *     most {@link #START_DISTANCE}
     * @param inclination angle between the second galaxy's disk and the plane of the orbit in degrees: 0 for a
     *     disk that turns with the orbit, 180 for one that turns against it
     */
    GalaxySimulation(
            ParticleCounts counts,
            long seed,
            double pace,
            double massRatio,
            double pericenter,
            double inclination,
            double openingAngle) {
        this.pace = pace;
        gravity = new BarnesHut(SOFTENING, openingAngle);
        leapfrog = new Leapfrog(gravity);
        var random = new SplittableRandom(seed);
        GalaxyModel secondModel = MODEL.scaled(massRatio);
        Galaxy first = Galaxy.sample(MODEL, counts, SOFTENING, random);
        Galaxy second = Galaxy.sample(secondModel, counts, SOFTENING, random);
        second.turn(Math.cos(TILT_AXIS_ANGLE), Math.sin(TILT_AXIS_ANGLE), 0, Math.toRadians(inclination));
        putOnParabolicOrbit(first, MODEL.mass(), second, secondModel.mass(), pericenter);
        starCount = 2 * counts.stars();
        int total = 2 * counts.total();
        position = new double[3 * total];
        velocity = new double[3 * total];
        mass = new double[total];
        copy(first, 0, counts.stars(), 0);
        copy(second, 0, counts.stars(), counts.stars());
        copy(first, counts.stars(), counts.halo(), starCount);
        copy(second, counts.stars(), counts.halo(), starCount + counts.halo());
        initialEnergy = measureEnergy();
        energy = initialEnergy;
    }

    /**
     * Places two galaxies on the parabolic orbit of two points of their masses, {@link #START_DISTANCE} apart
     * before the pericenter, with their center of mass at rest at the origin
     */
    private static void putOnParabolicOrbit(
            Galaxy first, double firstMass, Galaxy second, double secondMass, double pericenter) {
        double totalMass = firstMass + secondMass;
        // the second galaxy as seen from the first one, at the true anomaly ν (negative before the
        // pericenter). On a parabola the distance is r = 2 rₚ / (1 + cos ν)
        double anomaly = -Math.acos(2 * pericenter / START_DISTANCE - 1);
        double cos = Math.cos(anomaly);
        double sin = Math.sin(anomaly);
        // velocity on a parabola is √(G M / p) (-sin ν, 1 + cos ν) where p = 2 rₚ is its semi-latus rectum
        double speed = Math.sqrt(totalMass / (2 * pericenter));
        double vx = -speed * sin;
        double vy = speed * (1 + cos);
        // each galaxy is as far from the center of mass as the other one's share of the total mass
        double firstShare = -secondMass / totalMass;
        double secondShare = firstMass / totalMass;
        double x = START_DISTANCE * cos;
        double y = START_DISTANCE * sin;
        first.move(firstShare * x, firstShare * y, 0, firstShare * vx, firstShare * vy, 0);
        second.move(secondShare * x, secondShare * y, 0, secondShare * vx, secondShare * vy, 0);
    }

    private void copy(Galaxy galaxy, int first, int count, int index) {
        System.arraycopy(galaxy.position, 3 * first, position, 3 * index, 3 * count);
        System.arraycopy(galaxy.velocity, 3 * first, velocity, 3 * index, 3 * count);
        System.arraycopy(galaxy.mass, first, mass, index, count);
    }

    @Override
    public void apply(SimulationInput input) {
        switch (input) {
            case ParameterChange(String name, double value) -> setParameter(name, value);
            case Select(int star) -> select(star);
            case Deselect _ -> selected = NO_STAR;
            case VertexInput _ -> throw new InvalidInputException("Stars of the galaxies cannot be grabbed");
        }
    }

    private void setParameter(String name, double value) {
        switch (name) {
            case GalaxyDefinition.PACE -> pace = value;
            case GalaxyDefinition.OPENING_ANGLE -> gravity.setOpeningAngle(value);
            default ->
                throw new InvalidInputException(
                        "Parameter '%s' cannot change while the galaxies collide".formatted(name));
        }
    }

    private void select(int star) {
        if (star < 0 || star >= starCount) {
            throw new InvalidInputException("There is no star %d".formatted(star));
        }
        selected = star;
    }

    @Override
    public void step(double dt) {
        if (!(dt >= 0)) {
            throw new IllegalArgumentException("Time span must not be negative, but was " + dt);
        }
        double span = dt * pace / MYR_PER_TIME_UNIT;
        int steps = (int) Math.ceil(span / MAX_STEP);
        for (int i = 0; i < steps; i++) {
            leapfrog.step(position, velocity, mass, span / steps);
            time += span / steps;
            stepsSinceEnergy++;
            if (stepsSinceEnergy == STEPS_PER_ENERGY) {
                energy = measureEnergy();
                stepsSinceEnergy = 0;
            }
        }
    }

    /**
     * Measures the total energy: kinetic plus potential energy, which the energy meter sums up equally finely
     * whatever the opening angle of the gravity. After every step the leapfrog leaves positions and velocities
     * at the same time, as the energy needs them
     */
    private double measureEnergy() {
        double kinetic = 0;
        for (int particle = 0; particle < mass.length; particle++) {
            int i = 3 * particle;
            double speedSquared =
                    velocity[i] * velocity[i] + velocity[i + 1] * velocity[i + 1] + velocity[i + 2] * velocity[i + 2];
            kinetic += mass[particle] * speedSquared / 2;
        }
        return kinetic + energyMeter.potentialEnergy(position, mass);
    }

    @Override
    public double time() {
        return time * MYR_PER_TIME_UNIT;
    }

    /** Writes x, y and z of one star after another in kpc, then the readouts */
    @Override
    public void writeFrame(FloatBuffer frame) {
        for (int i = 0; i < 3 * starCount; i++) {
            frame.put((float) position[i]);
        }
        writeReadouts(frame);
    }

    /**
     * Writes the readouts: the pairs of all particles a direct sum of their gravity computes, the interactions
     * the tree computed instead in the last step, those the selected star summed up and how far its
     * acceleration deviated from the direct sum in percent (both NaN while no star is selected), and how much
     * the total energy changed since the start, in percent of it
     */
    private void writeReadouts(FloatBuffer frame) {
        long particles = mass.length;
        long pairs = particles * (particles - 1) / 2;
        frame.put((float) pairs);
        frame.put((float) gravity.interactions());
        if (selected == NO_STAR) {
            frame.put(Float.NaN).put(Float.NaN);
        } else {
            frame.put((float) gravity.interactionsOf(selected));
            frame.put((float) (100 * gravity.relativeErrorOf(selected)));
        }
        frame.put((float) (100 * (energy - initialEnergy) / Math.abs(initialEnergy)));
    }

    /**
     * Writes the x-ray as the scene of {@link GalaxyDefinition} lays it out: the cubes of the tree that acted
     * on the selected star as a whole in the last step, none while no star is selected
     */
    @Override
    public void writeXray(FloatBuffer frame) {
        int count = selected == NO_STAR ? 0 : gravity.cubesActingOn(selected, cubes);
        frame.put(Math.min(count, GalaxyDefinition.CUBE_CAPACITY));
        // cubes beyond the count are left over from earlier frames and get ignored
        for (double value : cubes) {
            frame.put((float) value);
        }
    }
}
