package dev.simlab.core;

/** Moves a grabbed vertex to a new position, does nothing to vertices nobody holds */
public record Drag(int vertex, double x, double y, double z) implements VertexInput {}
