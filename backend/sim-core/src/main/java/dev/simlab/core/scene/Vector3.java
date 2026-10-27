package dev.simlab.core.scene;

/** A point or a direction in the scene, in the simulation's unit of length */
public record Vector3(double x, double y, double z) {

    public Vector3 {
        if (!(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z))) {
            throw new IllegalArgumentException("Coordinates must be finite, but were (%s, %s, %s)".formatted(x, y, z));
        }
    }
}
