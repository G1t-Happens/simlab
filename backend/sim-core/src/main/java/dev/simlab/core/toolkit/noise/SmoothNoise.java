package dev.simlab.core.toolkit.noise;

/**
 * Noise over time: random values between -1 and 1 that change smoothly, the same for the same seed.
 *
 * <p>Draws a random value at every whole multiple of the period and interpolates between them with a
 * quintic curve, so the noise and its first two derivatives are continuous (value noise). The random
 * values come from hashing the seed with the multiple, so the noise can be evaluated at any time without
 * keeping state
 */
public final class SmoothNoise {

    /** spreads consecutive multiples over all 64 bits before hashing them, like SplitMix64 does */
    private static final long GOLDEN_GAMMA = 0x9e3779b97f4a7c15L;

    private final long seed;
    private final double period;

    /** @param period time between two random values, positive */
    public SmoothNoise(long seed, double period) {
        if (!(period > 0)) {
            throw new IllegalArgumentException("Period must be positive, but was " + period);
        }
        this.seed = seed;
        this.period = period;
    }

    /** Noise at a point in time, between -1 and 1 */
    public double valueAt(double time) {
        double scaled = time / period;
        double floor = Math.floor(scaled);
        double fraction = scaled - floor;
        double weight = fraction * fraction * fraction * (fraction * (fraction * 6 - 15) + 10);
        long multiple = (long) floor;
        double start = randomAt(multiple);
        return start + (randomAt(multiple + 1) - start) * weight;
    }

    /** Hashes the seed and a multiple of the period to a random value in [-1, 1), with SplitMix64's mixing */
    private double randomAt(long multiple) {
        long z = seed + multiple * GOLDEN_GAMMA;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-52 - 1;
    }
}
