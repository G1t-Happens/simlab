package dev.simlab.core.scene;

import java.util.List;

/**
 * A surface of triangles whose vertices move, like a cloth. Both sides are visible.
 *
 * <p>A frame holds x, y and z of one vertex after another
 *
 * @param triangles three vertex indices per triangle, counterclockwise as seen from the front
 * @param grabbable whether the pointer can grab vertices and drag them around. The simulation then gets
 *     {@link dev.simlab.core.Grab Grab}, {@link dev.simlab.core.Drag Drag} and
 *     {@link dev.simlab.core.Release Release} inputs for them
 */
public record Mesh(int vertexCount, List<Integer> triangles, Material material, boolean grabbable)
        implements SceneObject {

    public Mesh {
        triangles = List.copyOf(triangles);
        if (triangles.isEmpty() || triangles.size() % 3 != 0) {
            throw new IllegalArgumentException("A mesh needs at least one triangle of three indices, but got %d indices"
                    .formatted(triangles.size()));
        }
        for (int index : triangles) {
            if (index < 0 || index >= vertexCount) {
                throw new IllegalArgumentException(
                        "There is no vertex %d among the %d vertices of the mesh".formatted(index, vertexCount));
            }
        }
    }

    @Override
    public int frameFloats() {
        return Math.multiplyExact(3, vertexCount);
    }
}
