package dev.simlab.core;

public record ParameterChange(String name, double value) implements SimulationInput {}
