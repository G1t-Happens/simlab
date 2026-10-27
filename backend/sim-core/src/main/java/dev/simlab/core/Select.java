package dev.simlab.core;

/**
 * Selects a point in place of the one selected before, if any
 *
 * @param point index among the points of all point clouds of the {@linkplain dev.simlab.core.scene.Scene
 *     scene}
 */
public record Select(int point) implements Selection {}
