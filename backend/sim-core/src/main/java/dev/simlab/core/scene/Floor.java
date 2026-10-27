package dev.simlab.core.scene;

/**
 * The ground: a disk around the origin that faces up, as the {@linkplain Camera#up() camera} defines it. The
 * camera stays above the floor.
 *
 * <p>Doesn't move and takes up no floats in frames
 */
public record Floor(double radius) implements SceneObject {

    public Floor {
        if (!(radius > 0 && Double.isFinite(radius))) {
            throw new IllegalArgumentException("The radius must be positive and finite, but was " + radius);
        }
    }

    @Override
    public int frameFloats() {
        return 0;
    }
}
