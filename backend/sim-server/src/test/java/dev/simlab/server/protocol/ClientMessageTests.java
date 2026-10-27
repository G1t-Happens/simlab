package dev.simlab.server.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;

import dev.simlab.core.Deselect;
import dev.simlab.core.Drag;
import dev.simlab.core.Grab;
import dev.simlab.core.InvalidInputException;
import dev.simlab.core.ParameterChange;
import dev.simlab.core.Release;
import dev.simlab.core.Select;
import dev.simlab.core.simulations.cloth.ClothDefinition;
import dev.simlab.core.simulations.lorenz.LorenzDefinition;
import dev.simlab.server.protocol.ClientMessage.InputMessage;
import dev.simlab.server.protocol.ClientMessage.PauseMessage;
import dev.simlab.server.protocol.ClientMessage.PlayMessage;
import dev.simlab.server.protocol.ClientMessage.SpeedMessage;
import dev.simlab.server.protocol.ClientMessage.StepMessage;
import dev.simlab.server.protocol.ClientMessage.XrayMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Parses messages with the JSON mapper of the application, like the WebSocket handler does */
@JsonTest
class ClientMessageTests {

    private final JsonMapper jsonMapper;
    private final LorenzDefinition lorenz = new LorenzDefinition();
    private final ClothDefinition cloth = new ClothDefinition();

    ClientMessageTests(@Autowired JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Test
    void turnsParameterMessagesIntoParameterChanges() {
        InputMessage message = parseInput("{\"type\": \"param\", \"name\": \"rho\", \"value\": 20}");

        assertThat(message.toInput(lorenz)).isEqualTo(new ParameterChange("rho", 20));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"type\": \"param\", \"value\": 20}",
                "{\"type\": \"param\", \"name\": \"rho\"}",
                "{\"type\": \"param\", \"name\": \"rho\", \"value\": 61}",
                "{\"type\": \"param\", \"name\": \"gamma\", \"value\": 1}"
            })
    void rejectsParameterMessagesThatDoNotFitTheSimulation(String json) {
        InputMessage message = parseInput(json);

        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> message.toInput(lorenz));
    }

    @Test
    void turnsGrabDragAndReleaseMessagesIntoInputsForTheVertex() {
        assertThat(parseInput("{\"type\": \"grab\", \"vertex\": 12, \"x\": 0.5, \"y\": 1, \"z\": -0.25}")
                        .toInput(cloth))
                .isEqualTo(new Grab(12, 0.5, 1, -0.25));
        assertThat(parseInput("{\"type\": \"drag\", \"vertex\": 12, \"x\": 0.75, \"y\": 1.5, \"z\": 0}")
                        .toInput(cloth))
                .isEqualTo(new Drag(12, 0.75, 1.5, 0));
        assertThat(parseInput("{\"type\": \"release\", \"vertex\": 12}").toInput(cloth))
                .isEqualTo(new Release(12));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"type\": \"grab\", \"x\": 0, \"y\": 1, \"z\": 0}",
                "{\"type\": \"grab\", \"vertex\": 12, \"x\": 0, \"y\": 1}",
                "{\"type\": \"grab\", \"vertex\": 2304, \"x\": 0, \"y\": 1, \"z\": 0}",
                "{\"type\": \"drag\", \"vertex\": 12, \"y\": 1, \"z\": 0}",
                "{\"type\": \"drag\", \"vertex\": 12, \"x\": 1e999, \"y\": 1, \"z\": 0}",
                "{\"type\": \"release\"}",
                "{\"type\": \"release\", \"vertex\": -1}"
            })
    void rejectsVertexMessagesThatDoNotFitTheSimulation(String json) {
        InputMessage message = parseInput(json);

        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> message.toInput(cloth));
    }

    @Test
    void rejectsGrabbingASimulationWithoutVertices() {
        InputMessage message = parseInput("{\"type\": \"release\", \"vertex\": 0}");

        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> message.toInput(lorenz));
    }

    @Test
    void turnsSelectAndDeselectMessagesIntoSelections() {
        assertThat(parseInput("{\"type\": \"select\", \"point\": 42}").toInput(lorenz))
                .isEqualTo(new Select(42));
        assertThat(parseInput("{\"type\": \"deselect\"}").toInput(lorenz)).isEqualTo(new Deselect());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"type\": \"select\"}",
                "{\"type\": \"select\", \"point\": -1}",
                "{\"type\": \"select\", \"point\": 1000}"
            })
    void rejectsSelectMessagesThatDoNotFitTheSimulation(String json) {
        InputMessage message = parseInput(json);

        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> message.toInput(lorenz));
    }

    @Test
    void rejectsSelectingInASimulationWithoutSelectablePoints() {
        InputMessage message = parseInput("{\"type\": \"deselect\"}");

        assertThatExceptionOfType(InvalidInputException.class).isThrownBy(() -> message.toInput(cloth));
    }

    @Test
    void readsTheMessagesToTheClockOfTheRoom() {
        assertThat(parse("{\"type\": \"pause\"}")).isEqualTo(new PauseMessage());
        assertThat(parse("{\"type\": \"play\"}")).isEqualTo(new PlayMessage());
        assertThat(parse("{\"type\": \"step\"}")).isEqualTo(new StepMessage());
        assertThat(parse("{\"type\": \"speed\", \"value\": 0.25}")).isEqualTo(new SpeedMessage(0.25));
    }

    @Test
    void rejectsSpeedMessagesWithoutAValue() {
        var message = new SpeedMessage(null);

        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(message::speed)
                .withMessage("A speed message needs a value");
    }

    @Test
    void readsWhetherTheFramesShowTheXray() {
        assertThat(parse("{\"type\": \"xray\", \"shown\": true}")).isEqualTo(new XrayMessage(true));
        assertThat(new XrayMessage(false).xrayShown()).isFalse();
    }

    @Test
    void rejectsXrayMessagesThatDoNotSayWhetherTheXrayIsShown() {
        var message = new XrayMessage(null);

        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(message::xrayShown)
                .withMessage("An x-ray message needs to say whether the x-ray is shown");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"type\": \"teleport\"}",
                "{\"name\": \"rho\"}",
                "rho=20",
                "{\"type\": \"release\", \"vertex\": 1.5}",
                "{\"type\": \"speed\", \"value\": \"slow\"}",
                "{\"type\": \"xray\", \"shown\": \"maybe\"}"
            })
    void rejectsMalformedMessages(String json) {
        assertThatExceptionOfType(JacksonException.class).isThrownBy(() -> parse(json));
    }

    private ClientMessage parse(String json) {
        return jsonMapper.readValue(json, ClientMessage.class);
    }

    private InputMessage parseInput(String json) {
        if (parse(json) instanceof InputMessage message) {
            return message;
        }
        return fail("%s is no input of the simulation", json);
    }
}
