package dev.simlab.core;

/** Lets a grabbed vertex go, does nothing to vertices nobody holds */
public record Release(int vertex) implements VertexInput {}
