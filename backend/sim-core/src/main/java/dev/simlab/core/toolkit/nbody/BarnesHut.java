package dev.simlab.core.toolkit.nbody;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Gravity of many particles, approximated with a Barnes-Hut octree (Barnes and Hut, 1986): a cube of the
 * tree that looks small from where particles are acts on them as a single particle at its center of mass.
 * So computing the accelerations of n particles takes time in proportion to n log n instead of n².
 *
 * <p>Particle {@code i} has the coordinates {@code 3i}, {@code 3i + 1} and {@code 3i + 2} of a position
 * array. G = 1. Gravity is softened as if every particle's mass were spread over a Plummer sphere: two
 * particles at distance r attract each other with 1 / (r² + ε²) instead of 1 / r², which keeps close
 * encounters from flinging particles away.
 *
 * <p>Particles close to each other walk the tree as a group (Barnes, 1990): the walk lists the cubes and
 * particles that act on the group, then every particle of the group sums up their gravity, one interaction
 * per entry of the list. The potential energy takes the same walks with the potential in place of the
 * gravity. Groups run in parallel, the same calls in the same order give the same results.
 *
 * <p>Keeps the tree between calls to reuse its memory and to sort the particles into it starting from where
 * they were. Not thread-safe
 */
public final class BarnesHut {

    private static final int LEAF_CAPACITY = 16;

    /** max particles that walk the tree as a group */
    private static final int GROUP_SIZE = 64;

    /** cubes stop splitting at this depth, so particles at one point end the splitting */
    private static final int MAX_DEPTH = 32;

    /** tree-order place of a particle the last computation didn't include */
    private static final int NO_PLACE = -1;

    /** node of a list entry that stands for a single particle rather than a cube */
    private static final int NO_NODE = -1;

    /** interactions of the group a thread computes, one group after another */
    private static final ThreadLocal<Interactions> INTERACTIONS = ThreadLocal.withInitial(Interactions::new);

    private final double softeningSquared;

    private double nextOpeningAngle;

    /** squared opening angle of the last computation, later walks of its tree take it as well */
    private double openingAngleSquared;

    // the tree, one entry per node in every array. Root is node 0 and the children of a node (one per octant
    // that holds particles) follow one another. Every node holds a range of particles in tree order, its cube
    // is given by its center and half its edge, its moment by its center of mass and its mass
    private int nodeCount;
    private double[] cube = new double[0];
    private double[] moment = new double[0];
    private int[] depth = new int[0];
    private int[] firstChild = new int[0];
    private int[] childCount = new int[0];
    private int[] firstParticle = new int[0];
    private int[] particleCount = new int[0];

    // particles in tree order: the number of every particle, its coordinates and mass, the octant it lies in
    // while its node splits, its acceleration, its potential and the number of its interactions
    private int[] order = new int[0];
    private double[] sorted = new double[0];
    private byte[] octants = new byte[0];
    private double[] sortedAcceleration = new double[0];
    private double[] sortedPotential = new double[0];
    private int[] sortedInteractions = new int[0];

    /** where the range of each octant of a splitting node starts, followed by where the last one ends */
    private final int[] octantStart = new int[9];

    /** next place in the range of each octant, where the next particle in that octant moves to */
    private final int[] octantNext = new int[8];

    /** nodes whose particles walk the tree as a group, in the first {@link #groupCount} entries */
    private int[] groups = new int[0];

    private int groupCount;

    /**
     * @param softening softening length ε, positive
     * @param openingAngle largest ratio of a cube's edge to its distance from a group of particles at which
     *     the cube acts on the group as a whole. 0 sums the gravity of every pair of particles
     */
    public BarnesHut(double softening, double openingAngle) {
        if (!(softening > 0 && Double.isFinite(softening))) {
            throw new IllegalArgumentException("Softening must be positive, but was " + softening);
        }
        softeningSquared = softening * softening;
        setOpeningAngle(openingAngle);
    }

    /**
     * Sets the opening angle for the computations to come, what the tree reports about the last one stays
     * as it was
     */
    public void setOpeningAngle(double openingAngle) {
        if (!(openingAngle >= 0 && Double.isFinite(openingAngle))) {
            throw new IllegalArgumentException("Opening angle must not be negative, but was " + openingAngle);
        }
        nextOpeningAngle = openingAngle;
    }

    /**
     * Computes the acceleration that the gravity of all other particles gives to every particle.
     *
     * @param mass not negative, particles without mass feel gravity but exert none
     * @param acceleration receives three components per particle
     */
    public void accelerate(double[] position, double[] mass, double[] acceleration) {
        int count = mass.length;
        if (position.length != 3 * count || acceleration.length != 3 * count) {
            throw new IllegalArgumentException("Expected three coordinates per particle");
        }
        if (count == 0) {
            return;
        }
        openingAngleSquared = nextOpeningAngle * nextOpeningAngle;
        buildTree(position, mass);
        IntStream.range(0, groupCount).parallel().forEach(group -> accelerateGroup(groups[group]));
        for (int place = 0; place < count; place++) {
            int i = 3 * order[place];
            int j = 3 * place;
            acceleration[i] = sortedAcceleration[j];
            acceleration[i + 1] = sortedAcceleration[j + 1];
            acceleration[i + 2] = sortedAcceleration[j + 2];
        }
    }

    /**
     * Computes the potential energy: half the sum over all particles of a particle's mass times the softened
     * potential that the others create where it is, approximated by the tree like their gravity. Computes no
     * accelerations so it's faster than {@link #accelerate}, and what the tree reports afterwards is about
     * this computation, which knows no errors of accelerations
     *
     * @return the potential energy, 0 if fewer than two particles have mass
     */
    public double potentialEnergy(double[] position, double[] mass) {
        int count = mass.length;
        if (position.length != 3 * count) {
            throw new IllegalArgumentException("Expected three coordinates per particle");
        }
        if (count == 0) {
            return 0;
        }
        openingAngleSquared = nextOpeningAngle * nextOpeningAngle;
        buildTree(position, mass);
        IntStream.range(0, groupCount).parallel().forEach(group -> sumPotentials(groups[group]));
        // accelerations of earlier positions, which the tree would otherwise compare with these ones
        Arrays.fill(sortedAcceleration, Double.NaN);
        // in tree order, one particle after another, so the same positions give the same energy
        double energy = 0;
        for (int place = 0; place < count; place++) {
            energy += sorted[4 * place + 3] * sortedPotential[place];
        }
        return energy / 2;
    }

    /**
     * Number of interactions in the last computation, 0 before the first one: for every particle the cubes
     * and particles whose gravity or potential it summed up, itself included
     */
    public long interactions() {
        long total = 0;
        for (int count : sortedInteractions) {
            total += count;
        }
        return total;
    }

    /**
     * Number of cubes and particles whose gravity or potential a particle summed up in the last computation,
     * itself included (the entries of its group's list). 0 for a particle the last computation didn't
     * include, as before the first one
     */
    public int interactionsOf(int particle) {
        int place = placeOf(particle);
        return place == NO_PLACE ? 0 : sortedInteractions[place];
    }

    /**
     * Relative error of a particle's acceleration in the last computation: its distance from the acceleration
     * the direct sum over all other particles gives at the same positions, divided by the length of that.
     * Takes as long as the direct sum for one particle. NaN for a particle the last computation didn't
     * include, as before the first one, and after computing the potential energy (no accelerations there)
     */
    public double relativeErrorOf(int particle) {
        int place = placeOf(particle);
        if (place == NO_PLACE) {
            return Double.NaN;
        }
        double x = sorted[4 * place];
        double y = sorted[4 * place + 1];
        double z = sorted[4 * place + 2];
        double ax = 0;
        double ay = 0;
        double az = 0;
        // like in the tree the particle pulls on itself with no force, at distance 0
        for (int s = 0; s < sorted.length; s += 4) {
            double dx = sorted[s] - x;
            double dy = sorted[s + 1] - y;
            double dz = sorted[s + 2] - z;
            double inverse = 1 / Math.sqrt(dx * dx + dy * dy + dz * dz + softeningSquared);
            double pull = sorted[s + 3] * inverse * inverse * inverse;
            ax += pull * dx;
            ay += pull * dy;
            az += pull * dz;
        }
        double errorX = sortedAcceleration[3 * place] - ax;
        double errorY = sortedAcceleration[3 * place + 1] - ay;
        double errorZ = sortedAcceleration[3 * place + 2] - az;
        return Math.sqrt(errorX * errorX + errorY * errorY + errorZ * errorZ) / Math.sqrt(ax * ax + ay * ay + az * az);
    }

    /**
     * Writes the cubes that acted on a particle as a whole in the last computation, as many as fit into the
     * array: x, y and z of the center of one cube after another and half its edge. Its group's list holds
     * them, so this walks the tree once more for the group.
     *
     * @return how many cubes acted on the particle, can be more than fit. 0 for a particle the last
     *     computation didn't include, as before the first one
     */
    public int cubesActingOn(int particle, double[] cubes) {
        int place = placeOf(particle);
        if (place == NO_PLACE) {
            return 0;
        }
        Interactions interactions = listOf(groupOf(place));
        int count = 0;
        for (int entry = 0; entry < interactions.size; entry++) {
            int node = interactions.node[entry];
            if (node != NO_NODE) {
                if (4 * count + 4 <= cubes.length) {
                    // center of the cube and half its edge, as the tree keeps them
                    System.arraycopy(cube, 4 * node, cubes, 4 * count, 4);
                }
                count++;
            }
        }
        return count;
    }

    private int groupOf(int place) {
        for (int index = 0; index < groupCount; index++) {
            int group = groups[index];
            if (place >= firstParticle[group] && place < firstParticle[group] + particleCount[group]) {
                return group;
            }
        }
        throw new IllegalStateException("The groups hold every particle, but none holds place " + place);
    }

    private int placeOf(int particle) {
        for (int place = 0; place < order.length; place++) {
            if (order[place] == particle) {
                return place;
            }
        }
        return NO_PLACE;
    }

    private void buildTree(double[] position, double[] mass) {
        int count = mass.length;
        if (order.length != count) {
            order = IntStream.range(0, count).toArray();
            sorted = new double[4 * count];
            octants = new byte[count];
            sortedAcceleration = new double[3 * count];
            sortedPotential = new double[count];
            sortedInteractions = new int[count];
        }
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        // particles move little between calls, so in their previous order they're nearly sorted
        for (int place = 0; place < count; place++) {
            int particle = order[place];
            int i = 3 * particle;
            int s = 4 * place;
            sorted[s] = position[i];
            sorted[s + 1] = position[i + 1];
            sorted[s + 2] = position[i + 2];
            sorted[s + 3] = mass[particle];
            minX = Math.min(minX, position[i]);
            minY = Math.min(minY, position[i + 1]);
            minZ = Math.min(minZ, position[i + 2]);
            maxX = Math.max(maxX, position[i]);
            maxY = Math.max(maxY, position[i + 1]);
            maxZ = Math.max(maxZ, position[i + 2]);
        }
        // a bit larger than the particles span so rounding keeps every particle inside. Particles that all lie
        // at one point fit into a cube of any size
        double half = Math.max(Math.max(maxX - minX, maxY - minY), maxZ - minZ) * (0.5 + 1e-9);
        nodeCount = 0;
        addNode((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2, half > 0 ? half : 1, 0, 0, count);
        // nodes split in the order they were added, so the children of every node follow one another
        for (int node = 0; node < nodeCount; node++) {
            if (particleCount[node] > LEAF_CAPACITY && depth[node] < MAX_DEPTH) {
                split(node);
            }
        }
        computeMoments();
        if (groups.length < nodeCount) {
            groups = new int[nodeCount];
        }
        groupCount = 0;
        addGroups(0);
    }

    private void addNode(double x, double y, double z, double half, int nodeDepth, int first, int count) {
        if (nodeCount == depth.length) {
            int capacity = Math.max(64, 2 * nodeCount);
            cube = Arrays.copyOf(cube, 4 * capacity);
            moment = Arrays.copyOf(moment, 4 * capacity);
            depth = Arrays.copyOf(depth, capacity);
            firstChild = Arrays.copyOf(firstChild, capacity);
            childCount = Arrays.copyOf(childCount, capacity);
            firstParticle = Arrays.copyOf(firstParticle, capacity);
            particleCount = Arrays.copyOf(particleCount, capacity);
        }
        int node = nodeCount++;
        int c = 4 * node;
        cube[c] = x;
        cube[c + 1] = y;
        cube[c + 2] = z;
        cube[c + 3] = half;
        depth[node] = nodeDepth;
        firstChild[node] = -1;
        childCount[node] = 0;
        firstParticle[node] = first;
        particleCount[node] = count;
    }

    /** Sorts a node's particles by the octant they lie in and adds a child for every octant that holds any */
    private void split(int node) {
        int c = 4 * node;
        double centerX = cube[c];
        double centerY = cube[c + 1];
        double centerZ = cube[c + 2];
        int first = firstParticle[node];
        int end = first + particleCount[node];
        Arrays.fill(octantStart, 0);
        for (int place = first; place < end; place++) {
            int s = 4 * place;
            int octant = (sorted[s] >= centerX ? 1 : 0)
                    | (sorted[s + 1] >= centerY ? 2 : 0)
                    | (sorted[s + 2] >= centerZ ? 4 : 0);
            octants[place] = (byte) octant;
            octantStart[octant + 1]++;
        }
        octantStart[0] = first;
        for (int octant = 0; octant < 8; octant++) {
            octantStart[octant + 1] += octantStart[octant];
            octantNext[octant] = octantStart[octant];
        }
        // swap every particle to the next free place in its octant's range until each one is in its own
        for (int octant = 0; octant < 8; octant++) {
            while (octantNext[octant] < octantStart[octant + 1]) {
                int place = octantNext[octant];
                int target = octants[place];
                if (target == octant) {
                    octantNext[octant]++;
                } else {
                    swap(place, octantNext[target]++);
                }
            }
        }
        double half = cube[c + 3] / 2;
        firstChild[node] = nodeCount;
        for (int octant = 0; octant < 8; octant++) {
            int count = octantStart[octant + 1] - octantStart[octant];
            if (count > 0) {
                addNode(
                        centerX + ((octant & 1) == 0 ? -half : half),
                        centerY + ((octant & 2) == 0 ? -half : half),
                        centerZ + ((octant & 4) == 0 ? -half : half),
                        half,
                        depth[node] + 1,
                        octantStart[octant],
                        count);
            }
        }
        childCount[node] = nodeCount - firstChild[node];
    }

    private void swap(int a, int b) {
        int particle = order[a];
        order[a] = order[b];
        order[b] = particle;
        byte octant = octants[a];
        octants[a] = octants[b];
        octants[b] = octant;
        for (int i = 4 * a, j = 4 * b; i < 4 * a + 4; i++, j++) {
            double value = sorted[i];
            sorted[i] = sorted[j];
            sorted[j] = value;
        }
    }

    /** Mass and center of mass of every node, children before parents */
    private void computeMoments() {
        for (int node = nodeCount - 1; node >= 0; node--) {
            double nodeMass = 0;
            double x = 0;
            double y = 0;
            double z = 0;
            if (firstChild[node] < 0) {
                for (int s = 4 * firstParticle[node]; s < 4 * (firstParticle[node] + particleCount[node]); s += 4) {
                    nodeMass += sorted[s + 3];
                    x += sorted[s + 3] * sorted[s];
                    y += sorted[s + 3] * sorted[s + 1];
                    z += sorted[s + 3] * sorted[s + 2];
                }
            } else {
                for (int m = 4 * firstChild[node]; m < 4 * (firstChild[node] + childCount[node]); m += 4) {
                    nodeMass += moment[m + 3];
                    x += moment[m + 3] * moment[m];
                    y += moment[m + 3] * moment[m + 1];
                    z += moment[m + 3] * moment[m + 2];
                }
            }
            int m = 4 * node;
            moment[m] = nodeMass > 0 ? x / nodeMass : cube[m];
            moment[m + 1] = nodeMass > 0 ? y / nodeMass : cube[m + 1];
            moment[m + 2] = nodeMass > 0 ? z / nodeMass : cube[m + 2];
            moment[m + 3] = nodeMass;
        }
    }

    /** Makes every node of a subtree a group that holds few enough particles, unless its parent does */
    private void addGroups(int node) {
        if (particleCount[node] <= GROUP_SIZE || firstChild[node] < 0) {
            groups[groupCount++] = node;
        } else {
            for (int child = firstChild[node]; child < firstChild[node] + childCount[node]; child++) {
                addGroups(child);
            }
        }
    }

    /**
     * Walks the tree for a group of particles and returns the cubes and particles that act on it. The list
     * belongs to the calling thread
     */
    private Interactions listOf(int group) {
        Interactions interactions = INTERACTIONS.get();
        interactions.startGroup(sorted, firstParticle[group], firstParticle[group] + particleCount[group]);
        collect(0, interactions);
        return interactions;
    }

    private void accelerateGroup(int group) {
        Interactions interactions = listOf(group);
        int first = firstParticle[group];
        int end = first + particleCount[group];
        double[] xs = interactions.x;
        double[] ys = interactions.y;
        double[] zs = interactions.z;
        double[] masses = interactions.mass;
        int size = interactions.size;
        double epsilonSquared = softeningSquared;
        // a particle in its own group's list pulls on itself with no force, at distance 0
        for (int place = first; place < end; place++) {
            double x = sorted[4 * place];
            double y = sorted[4 * place + 1];
            double z = sorted[4 * place + 2];
            double ax = 0;
            double ay = 0;
            double az = 0;
            for (int j = 0; j < size; j++) {
                double dx = xs[j] - x;
                double dy = ys[j] - y;
                double dz = zs[j] - z;
                double inverse = 1 / Math.sqrt(dx * dx + dy * dy + dz * dz + epsilonSquared);
                double pull = masses[j] * inverse * inverse * inverse;
                ax += pull * dx;
                ay += pull * dy;
                az += pull * dz;
            }
            sortedAcceleration[3 * place] = ax;
            sortedAcceleration[3 * place + 1] = ay;
            sortedAcceleration[3 * place + 2] = az;
        }
        // every particle of the group interacted with every entry of the list
        Arrays.fill(sortedInteractions, first, end, size);
    }

    /**
     * Sums up the softened potential where every particle of a group is: minus the mass over the distance of
     * every entry of the group's list
     */
    private void sumPotentials(int group) {
        Interactions interactions = listOf(group);
        int first = firstParticle[group];
        int end = first + particleCount[group];
        double[] xs = interactions.x;
        double[] ys = interactions.y;
        double[] zs = interactions.z;
        double[] masses = interactions.mass;
        int size = interactions.size;
        double epsilonSquared = softeningSquared;
        double softening = Math.sqrt(epsilonSquared);
        for (int place = first; place < end; place++) {
            double x = sorted[4 * place];
            double y = sorted[4 * place + 1];
            double z = sorted[4 * place + 2];
            double massOverDistance = 0;
            for (int j = 0; j < size; j++) {
                double dx = xs[j] - x;
                double dy = ys[j] - y;
                double dz = zs[j] - z;
                massOverDistance += masses[j] / Math.sqrt(dx * dx + dy * dy + dz * dz + epsilonSquared);
            }
            // a particle in its own group's list adds its mass over ε to the sum (at distance 0), which
            // doesn't belong to its potential
            sortedPotential[place] = sorted[4 * place + 3] / softening - massOverDistance;
        }
        // every particle of the group interacted with every entry of the list
        Arrays.fill(sortedInteractions, first, end, size);
    }

    /**
     * Lists the gravity of a node among the interactions of a group: the node as a whole if its cube is far
     * enough from the group, otherwise its children or particles
     */
    private void collect(int node, Interactions interactions) {
        int m = 4 * node;
        if (moment[m + 3] == 0) {
            return;
        }
        double edge = 2 * cube[m + 3];
        double distanceSquared = interactions.distanceSquaredTo(moment[m], moment[m + 1], moment[m + 2]);
        if (edge * edge < openingAngleSquared * distanceSquared
                && interactions.isApartFrom(cube[m], cube[m + 1], cube[m + 2], cube[m + 3])) {
            interactions.add(moment[m], moment[m + 1], moment[m + 2], moment[m + 3], node);
        } else if (firstChild[node] < 0) {
            for (int s = 4 * firstParticle[node]; s < 4 * (firstParticle[node] + particleCount[node]); s += 4) {
                interactions.add(sorted[s], sorted[s + 1], sorted[s + 2], sorted[s + 3], NO_NODE);
            }
        } else {
            for (int child = firstChild[node]; child < firstChild[node] + childCount[node]; child++) {
                collect(child, interactions);
            }
        }
    }

    /**
     * Particles and cubes acting on a group of particles, each as a single particle at its center of mass,
     * plus the box that bounds the group
     */
    private static final class Interactions {

        double[] x = new double[1024];
        double[] y = new double[1024];
        double[] z = new double[1024];
        double[] mass = new double[1024];

        /** node of each cube that acts as a whole, {@link #NO_NODE} for a single particle */
        int[] node = new int[1024];

        int size;

        private double minX;
        private double minY;
        private double minZ;
        private double maxX;
        private double maxY;
        private double maxZ;

        /** Empties the list and bounds the particles of a group, given by their range in tree order */
        void startGroup(double[] sorted, int first, int end) {
            size = 0;
            minX = Double.POSITIVE_INFINITY;
            minY = Double.POSITIVE_INFINITY;
            minZ = Double.POSITIVE_INFINITY;
            maxX = Double.NEGATIVE_INFINITY;
            maxY = Double.NEGATIVE_INFINITY;
            maxZ = Double.NEGATIVE_INFINITY;
            for (int s = 4 * first; s < 4 * end; s += 4) {
                minX = Math.min(minX, sorted[s]);
                minY = Math.min(minY, sorted[s + 1]);
                minZ = Math.min(minZ, sorted[s + 2]);
                maxX = Math.max(maxX, sorted[s]);
                maxY = Math.max(maxY, sorted[s + 1]);
                maxZ = Math.max(maxZ, sorted[s + 2]);
            }
        }

        /** squared distance from a point to the group's box, 0 inside it */
        double distanceSquaredTo(double pointX, double pointY, double pointZ) {
            double dx = Math.max(0, Math.max(minX - pointX, pointX - maxX));
            double dy = Math.max(0, Math.max(minY - pointY, pointY - maxY));
            double dz = Math.max(0, Math.max(minZ - pointZ, pointZ - maxZ));
            return dx * dx + dy * dy + dz * dz;
        }

        /**
         * Whether a cube lies apart from the group's box. A cube holding particles of the group must never act
         * on them as a whole, however far away its center of mass lies
         */
        boolean isApartFrom(double centerX, double centerY, double centerZ, double half) {
            return centerX + half < minX
                    || centerX - half > maxX
                    || centerY + half < minY
                    || centerY - half > maxY
                    || centerZ + half < minZ
                    || centerZ - half > maxZ;
        }

        void add(double pointX, double pointY, double pointZ, double pointMass, int pointNode) {
            if (size == x.length) {
                x = Arrays.copyOf(x, 2 * size);
                y = Arrays.copyOf(y, 2 * size);
                z = Arrays.copyOf(z, 2 * size);
                mass = Arrays.copyOf(mass, 2 * size);
                node = Arrays.copyOf(node, 2 * size);
            }
            x[size] = pointX;
            y[size] = pointY;
            z[size] = pointZ;
            mass[size] = pointMass;
            node[size] = pointNode;
            size++;
        }
    }
}
