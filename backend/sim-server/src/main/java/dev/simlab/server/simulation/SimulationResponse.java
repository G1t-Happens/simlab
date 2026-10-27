package dev.simlab.server.simulation;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.simlab.core.ParameterSpec;
import dev.simlab.core.ParameterSpec.Effect;
import dev.simlab.core.SimulationDefinition;
import java.util.List;

/**
 * A simulation that rooms can be opened for
 *
 * @param type identifies the simulation, e.g. when opening a room
 * @param timeUnit unit of the simulated time, empty if the time has no unit
 */
record SimulationResponse(String type, String name, String description, String timeUnit, List<Parameter> parameters) {

    static SimulationResponse from(SimulationDefinition definition) {
        return new SimulationResponse(
                definition.type(),
                definition.name(),
                definition.description(),
                definition.timeUnit(),
                definition.parameters().stream().map(Parameter::from).toList());
    }

    /**
     * A parameter with its values and when a new value takes effect, its JSON names its kind like
     * {@code "kind": "Range"}
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "kind")
    sealed interface Parameter {

        static Parameter from(ParameterSpec spec) {
            return switch (spec) {
                case ParameterSpec.Range range ->
                    new Range(
                            range.name(),
                            range.label(),
                            range.unit(),
                            range.min(),
                            range.max(),
                            range.defaultValue(),
                            range.effect());
                case ParameterSpec.Choice choice ->
                    new Choice(
                            choice.name(),
                            choice.label(),
                            choice.options().stream()
                                    .map(option -> new Option(option.value(), option.label()))
                                    .toList(),
                            choice.defaultValue(),
                            choice.effect());
            };
        }
    }

    /** Any number between two bounds. The unit is empty for dimensionless parameters */
    record Range(String name, String label, String unit, double min, double max, double defaultValue, Effect effect)
            implements Parameter {}

    /** One of a few numbers, each of which names an option */
    record Choice(String name, String label, List<Option> options, double defaultValue, Effect effect)
            implements Parameter {}

    record Option(double value, String label) {}
}
