package dev.simlab.core;

/**
 * Input to a running simulation, applied between two steps.
 *
 * <p>Inputs from outside have to pass {@link SimulationDefinition#validate(SimulationInput)} before a
 * simulation applies them
 */
public sealed interface SimulationInput permits ParameterChange, VertexInput, Selection {}
