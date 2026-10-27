package dev.simlab.core.scene;

/**
 * Something the browser shows, in the scene or beside it. Each kind of object takes up a fixed number of
 * floats in every frame, which hold the parts of it that change
 */
public sealed interface SceneObject permits Cubes, Floor, Lines, Mesh, PointCloud, Readouts, Sphere {

    int frameFloats();
}
