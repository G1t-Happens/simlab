package dev.simlab.core.scene;

/**
 * Straight lines between moving points, like the springs of a cloth or the arrows of a vector field, each
 * colored by a value of its own.
 *
 * <p>A frame holds the number of lines it shows, then x, y and z of the start and of the end of one line
 * after another plus its value, as many lines as the capacity. Lines beyond the number are ignored
 *
 * @param capacity most lines a frame shows, at least 1
 * @param label what the lines show, for the legend
 * @param directed whether the lines point from start to end like arrows, which the browser shows by letting
 *     them brighten towards their end. Otherwise both ends look alike
 */
public record Lines(int capacity, String label, ColorScale scale, boolean directed) implements SceneObject {

    /** start and end coordinates plus the value */
    public static final int FLOATS_PER_LINE = 7;

    public Lines {
        if (capacity < 1) {
            throw new IllegalArgumentException("Lines need room for at least one line, but got " + capacity);
        }
        if (label.isBlank()) {
            throw new IllegalArgumentException("Lines need a label");
        }
    }

    @Override
    public int frameFloats() {
        return Math.addExact(1, Math.multiplyExact(FLOATS_PER_LINE, capacity));
    }
}
