package dev.simlab.core.scene;

/**
 * Cubes drawn as their edges, like the cells of an octree.
 *
 * <p>A frame holds the number of cubes it shows, then x, y and z of the center and half the edge of one cube
 * after another, as many cubes as the capacity. Cubes beyond the number are ignored
 *
 * @param capacity most cubes a frame shows, at least 1
 * @param label what the cubes show, for the legend
 */
public record Cubes(int capacity, String label, Color color) implements SceneObject {

    /** center coordinates and half the edge */
    public static final int FLOATS_PER_CUBE = 4;

    public Cubes {
        if (capacity < 1) {
            throw new IllegalArgumentException("Cubes need room for at least one cube, but got " + capacity);
        }
        if (label.isBlank()) {
            throw new IllegalArgumentException("Cubes need a label");
        }
    }

    @Override
    public int frameFloats() {
        return Math.addExact(1, Math.multiplyExact(FLOATS_PER_CUBE, capacity));
    }
}
