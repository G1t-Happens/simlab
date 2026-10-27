package dev.simlab.core;

/**
 * Input that grabs, drags or releases a vertex of the {@linkplain dev.simlab.core.scene.Scene#grabbableMesh()
 * grabbable mesh}, like a point of a cloth held by the mouse
 */
public sealed interface VertexInput extends SimulationInput permits Grab, Drag, Release {

    int vertex();
}
