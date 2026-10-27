package dev.simlab.core.toolkit.nbody;

/**
 * The leapfrog method: moves particles under their mutual gravity in drift-kick-drift steps, i.e. half a
 * step of motion at constant velocity, a full step of acceleration by the gravity at the positions halfway
 * through, then another half step of motion.
 *
 * <p>Second order and time-reversible, a step backwards undoes a step forwards. It conserves momentum if
 * every pair of particles pulls on each other equally hard (as in the direct sum of gravity), and being
 * symplectic it keeps the energy error bounded instead of letting it grow over many steps, so orbits
 * neither spiral in nor out. With gravity from a {@link BarnesHut} tree both hold only approximately.
 *
 * <p>Particles are numbered as in {@link BarnesHut}. A step evaluates gravity once and doesn't allocate
 * since the accelerations live in a buffer, so instances aren't thread-safe
 */
public final class Leapfrog {

    private final BarnesHut gravity;
    private double[] acceleration = new double[0];

    public Leapfrog(BarnesHut gravity) {
        this.gravity = gravity;
    }

    /**
     * Advances the particles by one step in time, in place
     *
     * @param velocity three components per particle
     * @param dt the time step, negative to step backwards
     */
    public void step(double[] position, double[] velocity, double[] mass, double dt) {
        if (velocity.length != position.length) {
            throw new IllegalArgumentException("Expected as many velocities as positions");
        }
        if (acceleration.length != position.length) {
            acceleration = new double[position.length];
        }
        drift(position, velocity, dt / 2);
        gravity.accelerate(position, mass, acceleration);
        for (int i = 0; i < velocity.length; i++) {
            velocity[i] += acceleration[i] * dt;
        }
        drift(position, velocity, dt / 2);
    }

    private static void drift(double[] position, double[] velocity, double dt) {
        for (int i = 0; i < position.length; i++) {
            position[i] += velocity[i] * dt;
        }
    }
}
