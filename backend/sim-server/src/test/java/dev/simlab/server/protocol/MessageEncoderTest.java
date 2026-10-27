package dev.simlab.server.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import dev.simlab.core.scene.Camera;
import dev.simlab.core.scene.Color;
import dev.simlab.core.scene.ColorScale;
import dev.simlab.core.scene.Cubes;
import dev.simlab.core.scene.Floor;
import dev.simlab.core.scene.Lines;
import dev.simlab.core.scene.Material;
import dev.simlab.core.scene.Material.Finish;
import dev.simlab.core.scene.Mesh;
import dev.simlab.core.scene.PointCloud;
import dev.simlab.core.scene.Readouts;
import dev.simlab.core.scene.Readouts.Readout;
import dev.simlab.core.scene.Scene;
import dev.simlab.core.scene.Sphere;
import dev.simlab.core.scene.Vector3;
import dev.simlab.server.FakeSimulation;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.json.JsonContent;

class MessageEncoderTest {

    @Test
    void writesAFrameAsHeaderInLittleEndianByteOrderFollowedByTheFloats() {
        var simulation = new FakeSimulation(1.0f, -2.0f, 0.5f);
        simulation.step(1.5);

        ByteBuffer message = MessageEncoder.encodeFrame(7, simulation, 3, 0);

        String messageType = "01";
        String reserved = "000000";
        String tick = "07000000";
        String time = "0000c03f"; // 1.5f
        String payloadLength = "0c000000"; // 12 bytes
        String payload = "0000803f" + "000000c0" + "0000003f"; // 1.0f, -2.0f, 0.5f
        assertThat(hex(message)).isEqualTo(messageType + reserved + tick + time + payloadLength + payload);
        assertThat(simulation.calls()).as("no x-ray computed").containsExactly("step 1.5");
    }

    @Test
    void writesTheXrayAfterTheFloatsOfAFrameThatShowsIt() {
        var simulation = new FakeSimulation(1.0f);
        simulation.setXray(0.5f, -2.0f);

        ByteBuffer message = MessageEncoder.encodeFrame(7, simulation, 1, 2);

        String payloadLength = "0c000000"; // 12 bytes
        String payload = "0000803f" + "0000003f" + "000000c0"; // 1.0f, then 0.5f and -2.0f of the x-ray
        assertThat(hex(message)).endsWith(payloadLength + payload);
    }

    @Test
    void writesTheTickAsUnsigned32BitIntegerThatWrapsAround() {
        var simulation = new FakeSimulation();

        assertThat(hex(MessageEncoder.encodeFrame(0xffff_ffffL, simulation, 0, 0)))
                .startsWith("01000000" + "ffffffff");
        assertThat(hex(MessageEncoder.encodeFrame(1L << 32, simulation, 0, 0))).startsWith("01000000" + "00000000");
    }

    @Test
    void rejectsSimulationsThatWriteFewerFloatsThanTheirFramesHold() {
        var simulation = new FakeSimulation(1.0f);

        assertThatIllegalStateException()
                .isThrownBy(() -> MessageEncoder.encodeFrame(1, simulation, 2, 0))
                .withMessage("Simulation wrote 1 into its frame of 2 floats");
    }

    @Test
    void rejectsSimulationsThatWriteMoreFloatsThanTheirFramesHold() {
        var simulation = new FakeSimulation(1.0f, 2.0f, 3.0f);

        assertThatExceptionOfType(BufferOverflowException.class)
                .isThrownBy(() -> MessageEncoder.encodeFrame(1, simulation, 2, 0));
        assertThatIllegalStateException()
                .as("into the room of the x-ray")
                .isThrownBy(() -> MessageEncoder.encodeFrame(1, simulation, 2, 4))
                .withMessage("Simulation wrote 3 into its frame of 2 floats");
    }

    @Test
    void rejectsSimulationsThatWriteAnotherNumberOfFloatsThanTheirXraysHold() {
        var simulation = new FakeSimulation(1.0f);
        simulation.setXray(2.0f);

        assertThatIllegalStateException()
                .isThrownBy(() -> MessageEncoder.encodeFrame(1, simulation, 1, 2))
                .withMessage("Simulation wrote 1 into its x-ray of 2 floats");
    }

    @Test
    void writesASceneAsJsonThatNamesTheKindOfEveryObject() {
        var fabric = new Material(Color.rgb(0xd9513c), Finish.FABRIC);
        var scene = new Scene(
                new Camera(new Vector3(4, 2, 3), new Vector3(0, 1, 0), new Vector3(0, 1, 0), true),
                List.of(
                        new PointCloud(2, Color.rgb(0x000000), Color.rgb(0xffffff), 32, true, true),
                        new Mesh(3, List.of(0, 1, 2), fabric, true),
                        new Sphere(new Material(Color.rgb(0xc9d1d9), Finish.GLOSSY)),
                        new Floor(4),
                        new Readouts(List.of(new Readout("speed", "|w|", "m/s")))),
                List.of(
                        new Lines(
                                2,
                                "Springs",
                                new ColorScale("ε", "%", -2, 2, List.of(Color.rgb(0x0000ff), Color.rgb(0xff0000))),
                                false),
                        new Cubes(3, "Cells", Color.rgb(0x00ff00))));

        ByteBuffer message = MessageEncoder.encodeScene(scene);

        String messageType = "02";
        String reserved = "000000";
        String tick = "00000000";
        String time = "00000000";
        assertThat(hex(message)).startsWith(messageType + reserved + tick + time);
        int payloadLength = message.duplicate().order(ByteOrder.LITTLE_ENDIAN).getInt(12);
        assertThat(message.remaining()).isEqualTo(MessageEncoder.HEADER_SIZE + payloadLength);
        String json = StandardCharsets.UTF_8
                .decode(message.slice(MessageEncoder.HEADER_SIZE, payloadLength))
                .toString();
        assertThat(new JsonContent(json)).isStrictlyEqualTo("""
                {
                  "camera": {
                    "position": {"x": 4.0, "y": 2.0, "z": 3.0},
                    "target": {"x": 0.0, "y": 1.0, "z": 0.0},
                    "up": {"x": 0.0, "y": 1.0, "z": 0.0},
                    "orbiting": true
                  },
                  "objects": [
                    {
                      "kind": "PointCloud",
                      "count": 2,
                      "firstColor": {"red": 0, "green": 0, "blue": 0},
                      "lastColor": {"red": 255, "green": 255, "blue": 255},
                      "trailLength": 32,
                      "glowing": true,
                      "selectable": true
                    },
                    {
                      "kind": "Mesh",
                      "vertexCount": 3,
                      "triangles": [0, 1, 2],
                      "material": {"color": {"red": 217, "green": 81, "blue": 60}, "finish": "FABRIC"},
                      "grabbable": true
                    },
                    {
                      "kind": "Sphere",
                      "material": {"color": {"red": 201, "green": 209, "blue": 217}, "finish": "GLOSSY"}
                    },
                    {"kind": "Floor", "radius": 4.0},
                    {"kind": "Readouts", "readouts": [{"name": "speed", "label": "|w|", "unit": "m/s"}]}
                  ],
                  "xray": [
                    {
                      "kind": "Lines",
                      "capacity": 2,
                      "label": "Springs",
                      "scale": {
                        "label": "ε",
                        "unit": "%",
                        "min": -2.0,
                        "max": 2.0,
                        "colors": [{"red": 0, "green": 0, "blue": 255}, {"red": 255, "green": 0, "blue": 0}]
                      },
                      "directed": false
                    },
                    {"kind": "Cubes", "capacity": 3, "label": "Cells", "color": {"red": 0, "green": 255, "blue": 0}}
                  ]
                }
                """);
    }

    /** remaining bytes of a message in hex, without consuming them */
    private static String hex(ByteBuffer message) {
        byte[] bytes = new byte[message.remaining()];
        message.duplicate().get(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
