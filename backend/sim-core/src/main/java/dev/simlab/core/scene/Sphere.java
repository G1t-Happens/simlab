package dev.simlab.core.scene;

/**
 * A sphere that moves and changes its size, like a ball or an obstacle.
 *
 * <p>A frame holds x, y and z of its center, then its radius
 */
public record Sphere(Material material) implements SceneObject {

    @Override
    public int frameFloats() {
        return 4;
    }
}
