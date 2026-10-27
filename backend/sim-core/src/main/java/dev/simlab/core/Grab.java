package dev.simlab.core;

/** Grabs a vertex and holds it at a position until a {@link Release} lets it go */
public record Grab(int vertex, double x, double y, double z) implements VertexInput {}
