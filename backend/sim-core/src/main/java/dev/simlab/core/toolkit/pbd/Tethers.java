package dev.simlab.core.toolkit.pbd;

/**
 * Long-range attachments: keep particles from moving farther than a given distance from their anchors (like
 * the pinned corners of a cloth) but let them come closer.
 *
 * <p>Distance constraints carry the weight of a hanging cloth from particle to particle, so a few passes over
 * them leave the cloth stretched, most of all next to its pins. Tethers carry it to the pins in one go (Kim,
 * Chentanez and Müller-Fischer: Long Range Attachments, 2012). Projecting doesn't allocate
 */
public final class Tethers {

    private final int[] particle;
    private final int[] anchor;
    private final double[] length;
    private int size;

    public Tethers(int capacity) {
        particle = new int[capacity];
        anchor = new int[capacity];
        length = new double[capacity];
    }

    /**
     * Ties a particle to an anchor, a particle that doesn't move
     *
     * @param length largest distance between the particle and the anchor
     * @throws IllegalStateException if there's no room for another tether
     */
    public void add(int particle, int anchor, double length) {
        if (size == this.particle.length) {
            throw new IllegalStateException("There is no room for more than %d tethers".formatted(size));
        }
        this.particle[size] = particle;
        this.anchor[size] = anchor;
        this.length[size] = length;
        size++;
    }

    public int size() {
        return size;
    }

    /**
     * Pulls every particle that's too far from its anchor back towards it
     *
     * @param position corrected in place
     * @param inverseMass 0 for particles that must not move
     */
    public void project(double[] position, double[] inverseMass) {
        for (int tether = 0; tether < size; tether++) {
            if (inverseMass[particle[tether]] == 0) {
                continue;
            }
            int i = 3 * particle[tether];
            int j = 3 * anchor[tether];
            double dx = position[i] - position[j];
            double dy = position[i + 1] - position[j + 1];
            double dz = position[i + 2] - position[j + 2];
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            double maxLength = length[tether];
            if (distanceSquared > maxLength * maxLength) {
                double scale = maxLength / Math.sqrt(distanceSquared);
                position[i] = position[j] + dx * scale;
                position[i + 1] = position[j + 1] + dy * scale;
                position[i + 2] = position[j + 2] + dz * scale;
            }
        }
    }
}
