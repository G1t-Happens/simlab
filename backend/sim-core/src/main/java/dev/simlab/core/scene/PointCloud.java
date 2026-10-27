package dev.simlab.core.scene;

/**
 * Points that move, like particles or stars.
 *
 * <p>A frame holds x, y and z of one point after another
 *
 * @param firstColor color of the first point. The colors of the points in between blend from the first to
 *     the last color, so points next to each other in the frames look alike
 * @param trailLength number of the latest frames the trail of a point connects, 0 for no trails
 * @param glowing whether the points shine like stars, with light that adds up where they crowd and glows.
 *     Otherwise they're drawn as dots
 * @param selectable whether the pointer can select a point, whose values the simulation then reports in its
 *     {@linkplain Readouts readouts}. The simulation then gets {@link dev.simlab.core.Select Select} and
 *     {@link dev.simlab.core.Deselect Deselect} inputs for it
 */
public record PointCloud(
        int count, Color firstColor, Color lastColor, int trailLength, boolean glowing, boolean selectable)
        implements SceneObject {

    public PointCloud {
        if (count < 1) {
            throw new IllegalArgumentException("A point cloud needs at least one point, but got " + count);
        }
        if (trailLength < 0 || trailLength == 1) {
            throw new IllegalArgumentException("A trail connects no frames or at least two, but got " + trailLength);
        }
    }

    @Override
    public int frameFloats() {
        return Math.multiplyExact(3, count);
    }
}
