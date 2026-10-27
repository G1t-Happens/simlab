package dev.simlab.core.toolkit.pbd;

/**
 * Constraints that keep pairs of particles at a distance, solved with extended position-based dynamics
 * (XPBD).
 *
 * <p>Particle {@code i} has the coordinates {@code 3i}, {@code 3i + 1} and {@code 3i + 2} of a position
 * array. Projecting doesn't allocate
 */
public final class DistanceConstraints {

    private final int[] first;
    private final int[] second;
    private final double[] restLength;
    private final double[] compliance;
    private int size;

    public DistanceConstraints(int capacity) {
        first = new int[capacity];
        second = new int[capacity];
        restLength = new double[capacity];
        compliance = new double[capacity];
    }

    /**
     * Adds a constraint between two particles
     *
     * @param compliance inverse stiffness in m/N, 0 for a rigid constraint
     * @throws IllegalStateException if there's no room for another constraint
     */
    public void add(int a, int b, double restLength, double compliance) {
        if (size == first.length) {
            throw new IllegalStateException("There is no room for more than %d constraints".formatted(size));
        }
        first[size] = a;
        second[size] = b;
        this.restLength[size] = restLength;
        this.compliance[size] = compliance;
        size++;
    }

    public int size() {
        return size;
    }

    /** Particle at the first end of a constraint. Constraints are numbered in the order they were added */
    public int firstParticle(int constraint) {
        return first[checked(constraint)];
    }

    public int secondParticle(int constraint) {
        return second[checked(constraint)];
    }

    /**
     * Strain of a constraint: how much farther apart than its rest length its particles lie, relative to the
     * rest length. Negative where they lie closer
     */
    public double strain(int constraint, double[] position) {
        int i = 3 * first[checked(constraint)];
        int j = 3 * second[constraint];
        double dx = position[j] - position[i];
        double dy = position[j + 1] - position[i + 1];
        double dz = position[j + 2] - position[i + 2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz) / restLength[constraint] - 1;
    }

    private int checked(int constraint) {
        if (constraint < 0 || constraint >= size) {
            throw new IndexOutOfBoundsException("There is no constraint %d among %d".formatted(constraint, size));
        }
        return constraint;
    }

    /**
     * Moves the particles towards satisfying the constraints, one after the other in the order they were
     * added (Gauss-Seidel).
     *
     * <p>A single XPBD iteration with the Lagrange multipliers starting at 0, as in a scheme of many small
     * substeps with one iteration each. The stiffness of a constraint with compliance then doesn't depend on
     * the length of the substep
     *
     * @param position corrected in place
     * @param inverseMass 0 for particles that must not move
     * @param substep length of the substep in seconds, positive
     */
    public void project(double[] position, double[] inverseMass, double substep) {
        double complianceScale = 1 / (substep * substep);
        for (int constraint = 0; constraint < size; constraint++) {
            int a = first[constraint];
            int b = second[constraint];
            double inverseMassA = inverseMass[a];
            double inverseMassB = inverseMass[b];
            if (inverseMassA + inverseMassB == 0) {
                continue;
            }
            int i = 3 * a;
            int j = 3 * b;
            double dx = position[j] - position[i];
            double dy = position[j + 1] - position[i + 1];
            double dz = position[j + 2] - position[i + 2];
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length == 0) {
                // without a direction there's no way to separate the particles
                continue;
            }
            double weight = inverseMassA + inverseMassB + compliance[constraint] * complianceScale;
            double correction = (length - restLength[constraint]) / (weight * length);
            position[i] += inverseMassA * correction * dx;
            position[i + 1] += inverseMassA * correction * dy;
            position[i + 2] += inverseMassA * correction * dz;
            position[j] -= inverseMassB * correction * dx;
            position[j + 1] -= inverseMassB * correction * dy;
            position[j + 2] -= inverseMassB * correction * dz;
        }
    }
}
