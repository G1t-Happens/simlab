package dev.simlab.core;

import static java.util.stream.Collectors.joining;

import dev.simlab.core.ParameterSpec.Choice;
import dev.simlab.core.ParameterSpec.Effect;
import dev.simlab.core.ParameterSpec.Range;
import dev.simlab.core.scene.Mesh;
import dev.simlab.core.scene.Scene;
import java.util.List;
import java.util.Map;

/**
 * Describes a kind of simulation and creates instances of it.
 *
 * <p>The server finds every implementation in the packages of {@code sim-core} by itself and creates it
 * with its no-arg constructor
 */
public interface SimulationDefinition {

    /**
     * Type that identifies the simulation in the HTTP API and in browser addresses, like {@code lorenz}:
     * lower-case letters and digits, single hyphens between words
     */
    String type();

    String name();

    String description();

    /** Unit of the {@linkplain Simulation#time() simulated time} like {@code s}, empty if the time has no unit */
    String timeUnit();

    List<ParameterSpec> parameters();

    /** The scene every simulation of this kind shows, it lays out the frames */
    Scene scene();

    /**
     * Creates a simulation in its initial state
     *
     * @param values replace the defaults of parameters, by name, as {@link #validateValues} accepts them
     */
    Simulation create(Map<String, Double> values);

    /**
     * Checks that parameter values fit this simulation, like the values a simulation starts with have to:
     * every parameter is known and every value lies within its range
     *
     * @throws InvalidInputException if the values don't fit this simulation
     */
    default Map<String, Double> validateValues(Map<String, Double> values) {
        values.forEach(this::checkedParameter);
        return values;
    }

    /**
     * Checks that an input fits this simulation, as every input from outside has to before a simulation
     * applies it.
     *
     * <p>By default accepts changes of {@linkplain Effect#LIVE live} parameters within their ranges, inputs for
     * the vertices of the {@linkplain Scene#grabbableMesh() grabbable mesh} at finite positions and selections
     * of the points of {@linkplain Scene#isSelectable(int) selectable} point clouds. Simulations that need
     * stricter checks override it
     *
     * @throws InvalidInputException if the input doesn't fit this simulation
     */
    default <T extends SimulationInput> T validate(T input) {
        switch (input) {
            case ParameterChange change -> validateParameterChange(change);
            case VertexInput vertexInput -> validateVertexInput(vertexInput);
            case Selection selection -> validateSelection(selection);
        }
        return input;
    }

    private void validateParameterChange(ParameterChange change) {
        if (checkedParameter(change.name(), change.value()).effect() != Effect.LIVE) {
            throw new InvalidInputException(
                    "Parameter '%s' takes effect when the simulation starts over".formatted(change.name()));
        }
    }

    private ParameterSpec checkedParameter(String name, double value) {
        ParameterSpec parameter = parameters().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new InvalidInputException("Unknown parameter '%s'".formatted(name)));
        if (!parameter.accepts(value)) {
            throw new InvalidInputException("Parameter '%s' must %s".formatted(name, permittedValues(parameter)));
        }
        return parameter;
    }

    /** Describes the values a parameter can take, like {@code lie between 0.0 and 60.0} */
    private static String permittedValues(ParameterSpec parameter) {
        return switch (parameter) {
            case Range range -> "lie between %s and %s".formatted(range.min(), range.max());
            case Choice choice ->
                "be one of "
                        + choice.options().stream()
                                .map(option -> String.valueOf(option.value()))
                                .collect(joining(", "));
        };
    }

    private void validateVertexInput(VertexInput input) {
        Mesh mesh = scene().grabbableMesh()
                .orElseThrow(() -> new InvalidInputException("%s has no vertices to grab".formatted(name())));
        int vertex = input.vertex();
        if (vertex < 0 || vertex >= mesh.vertexCount()) {
            throw new InvalidInputException(
                    "There is no vertex %d, the mesh has %d vertices".formatted(vertex, mesh.vertexCount()));
        }
        boolean finite = switch (input) {
            case Grab grab -> isFinite(grab.x(), grab.y(), grab.z());
            case Drag drag -> isFinite(drag.x(), drag.y(), drag.z());
            case Release _ -> true;
        };
        if (!finite) {
            throw new InvalidInputException("The position of vertex %d must be finite".formatted(vertex));
        }
    }

    private void validateSelection(Selection selection) {
        Scene scene = scene();
        if (!scene.hasSelectablePoints()) {
            throw new InvalidInputException("%s has no points to select".formatted(name()));
        }
        if (selection instanceof Select(int point) && !scene.isSelectable(point)) {
            throw new InvalidInputException("There is no point %d to select".formatted(point));
        }
    }

    private static boolean isFinite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }
}
