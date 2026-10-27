package dev.simlab.core;

/**
 * Input that selects a point of a {@linkplain dev.simlab.core.scene.PointCloud#selectable() selectable} point
 * cloud or none, like a star the pointer clicks. The simulation reports the values of the selected point in
 * its {@linkplain dev.simlab.core.scene.Readouts readouts}
 */
public sealed interface Selection extends SimulationInput permits Select, Deselect {}
