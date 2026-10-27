package dev.simlab.server.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.simlab.core.Deselect;
import dev.simlab.core.Drag;
import dev.simlab.core.Grab;
import dev.simlab.core.InvalidInputException;
import dev.simlab.core.ParameterChange;
import dev.simlab.core.Release;
import dev.simlab.core.Select;
import dev.simlab.core.SimulationDefinition;
import dev.simlab.core.SimulationInput;
import org.jspecify.annotations.Nullable;

/**
 * A JSON message from a browser to its room, told apart by the property {@code type}: an input of the
 * simulation, a message to the room's clock (which plays the simulation), or one that shows its x-ray.
 * Properties that an invalid message leaves out are {@code null}
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ClientMessage.ParameterMessage.class, name = "param"),
    @JsonSubTypes.Type(value = ClientMessage.GrabMessage.class, name = "grab"),
    @JsonSubTypes.Type(value = ClientMessage.DragMessage.class, name = "drag"),
    @JsonSubTypes.Type(value = ClientMessage.ReleaseMessage.class, name = "release"),
    @JsonSubTypes.Type(value = ClientMessage.SelectMessage.class, name = "select"),
    @JsonSubTypes.Type(value = ClientMessage.DeselectMessage.class, name = "deselect"),
    @JsonSubTypes.Type(value = ClientMessage.PauseMessage.class, name = "pause"),
    @JsonSubTypes.Type(value = ClientMessage.PlayMessage.class, name = "play"),
    @JsonSubTypes.Type(value = ClientMessage.StepMessage.class, name = "step"),
    @JsonSubTypes.Type(value = ClientMessage.SpeedMessage.class, name = "speed"),
    @JsonSubTypes.Type(value = ClientMessage.XrayMessage.class, name = "xray")
})
public sealed interface ClientMessage {

    sealed interface InputMessage extends ClientMessage {

        /**
         * Validates this message against the simulation of the room and turns it into an input
         *
         * @throws InvalidInputException if the message doesn't fit the simulation
         */
        SimulationInput toInput(SimulationDefinition definition);
    }

    /** Changes a parameter, e.g. {@code {"type": "param", "name": "rho", "value": 28}} */
    record ParameterMessage(@Nullable String name, @Nullable Double value) implements InputMessage {

        @Override
        public SimulationInput toInput(SimulationDefinition definition) {
            if (name == null || value == null) {
                throw new InvalidInputException("A param message needs a name and a value");
            }
            return definition.validate(new ParameterChange(name, value));
        }
    }

    /**
     * Grabs a vertex and holds it at a position, e.g.
     * {@code {"type": "grab", "vertex": 1200, "x": 0.1, "y": 1.6, "z": 0.4}}
     */
    record GrabMessage(
            @Nullable Integer vertex,
            @Nullable Double x,
            @Nullable Double y,
            @Nullable Double z) implements InputMessage {

        @Override
        public SimulationInput toInput(SimulationDefinition definition) {
            if (vertex == null || x == null || y == null || z == null) {
                throw new InvalidInputException("A grab message needs a vertex and its position x, y and z");
            }
            return definition.validate(new Grab(vertex, x, y, z));
        }
    }

    /** Moves a grabbed vertex, e.g. {@code {"type": "drag", "vertex": 1200, "x": 0.2, "y": 1.7, "z": 0.4}} */
    record DragMessage(
            @Nullable Integer vertex,
            @Nullable Double x,
            @Nullable Double y,
            @Nullable Double z) implements InputMessage {

        @Override
        public SimulationInput toInput(SimulationDefinition definition) {
            if (vertex == null || x == null || y == null || z == null) {
                throw new InvalidInputException("A drag message needs a vertex and its position x, y and z");
            }
            return definition.validate(new Drag(vertex, x, y, z));
        }
    }

    /** Lets a grabbed vertex go, e.g. {@code {"type": "release", "vertex": 1200}} */
    record ReleaseMessage(@Nullable Integer vertex) implements InputMessage {

        @Override
        public SimulationInput toInput(SimulationDefinition definition) {
            if (vertex == null) {
                throw new InvalidInputException("A release message needs a vertex");
            }
            return definition.validate(new Release(vertex));
        }
    }

    /**
     * Selects a point whose values the simulation then reports, e.g. {@code {"type": "select", "point": 42}}
     *
     * @param point index among the points of all point clouds of the scene
     */
    record SelectMessage(@Nullable Integer point) implements InputMessage {

        @Override
        public SimulationInput toInput(SimulationDefinition definition) {
            if (point == null) {
                throw new InvalidInputException("A select message needs a point");
            }
            return definition.validate(new Select(point));
        }
    }

    /** Lets go of the selected point, {@code {"type": "deselect"}} */
    record DeselectMessage() implements InputMessage {

        @Override
        public SimulationInput toInput(SimulationDefinition definition) {
            return definition.validate(new Deselect());
        }
    }

    /** Stops the clock of the room, {@code {"type": "pause"}} */
    record PauseMessage() implements ClientMessage {}

    /** Starts the clock of the room again, {@code {"type": "play"}} */
    record PlayMessage() implements ClientMessage {}

    /** Advances a paused room by one tick, {@code {"type": "step"}} */
    record StepMessage() implements ClientMessage {}

    /**
     * Plays the simulation at a share of real time, e.g. {@code {"type": "speed", "value": 0.25}} for slow
     * motion at a quarter of real time
     */
    record SpeedMessage(@Nullable Double value) implements ClientMessage {

        /**
         * The share of real time
         *
         * @throws InvalidInputException if the message lacks it
         */
        public double speed() {
            if (value == null) {
                throw new InvalidInputException("A speed message needs a value");
            }
            return value;
        }
    }

    /**
     * Shows the x-ray of the simulation in the frames of the room or stops showing it, e.g.
     * {@code {"type": "xray", "shown": true}}
     */
    record XrayMessage(@Nullable Boolean shown) implements ClientMessage {

        /**
         * Whether the frames show the x-ray
         *
         * @throws InvalidInputException if the message lacks it
         */
        public boolean xrayShown() {
            if (shown == null) {
                throw new InvalidInputException("An x-ray message needs to say whether the x-ray is shown");
            }
            return shown;
        }
    }
}
