package dev.simlab.server.protocol;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.simlab.core.Simulation;
import dev.simlab.core.scene.Scene;
import dev.simlab.core.scene.SceneObject;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Encodes the binary messages from rooms to browsers.
 *
 * <p>Every message starts with a 16-byte header, little-endian:
 *
 * <pre>
 * offset  type     field
 *      0  uint8    message type, see {@link MessageType}
 *      1           reserved, 3 bytes of 0
 *      4  uint32   tick number
 *      8  float32  simulation time
 *     12  uint32   length of the payload in bytes
 *     16           payload
 * </pre>
 *
 * <p>The payload of a scene message is the scene as JSON in UTF-8, that of a frame message the floats of the
 * frame followed by those of the x-ray while the room shows it (the payload length tells which). With a
 * 16-byte header the floats stay aligned to four bytes, so the browser can read them through a typed array
 * without copying
 */
public final class MessageEncoder {

    public static final int HEADER_SIZE = 16;

    /** JSON of scenes, independent of the settings of the HTTP API */
    private static final JsonMapper SCENE_MAPPER = JsonMapper.builder()
            .addMixIn(SceneObject.class, SceneObjectMixin.class)
            .build();

    private MessageEncoder() {}

    /**
     * Encodes a scene as scene message, with 0 as tick and time
     *
     * @return a buffer positioned at the start of the message
     */
    public static ByteBuffer encodeScene(Scene scene) {
        byte[] json = SCENE_MAPPER.writeValueAsBytes(scene);
        return header(MessageType.SCENE, 0, 0, json.length).put(json).rewind();
    }

    /**
     * Encodes the current state of a simulation as frame message: the floats of the frame, followed by those of
     * the x-ray if the frame shows it
     *
     * @param tick sent as uint32, so it wraps around after 2<sup>32</sup> ticks
     * @param frameFloats floats in every frame of the simulation, as its scene lays them out
     * @param xrayFloats floats of the x-ray as the scene lays them out, 0 for a frame without the x-ray
     * @return a buffer positioned at the start of the message
     * @throws IllegalStateException if the simulation writes another number of floats than the scene lays out
     * @throws BufferOverflowException if the simulation writes more floats than the message holds
     */
    public static ByteBuffer encodeFrame(long tick, Simulation simulation, int frameFloats, int xrayFloats) {
        int floats = Math.addExact(frameFloats, xrayFloats);
        ByteBuffer message =
                header(MessageType.FRAME, tick, simulation.time(), Math.multiplyExact(floats, Float.BYTES));
        // the view shares the payload and the byte order of the message
        FloatBuffer frame = message.asFloatBuffer();
        simulation.writeFrame(frame);
        requireFloats("frame", frame.position(), frameFloats);
        if (xrayFloats > 0) {
            simulation.writeXray(frame);
            requireFloats("x-ray", frame.position() - frameFloats, xrayFloats);
        }
        return message.rewind();
    }

    private static void requireFloats(String part, int written, int expected) {
        if (written != expected) {
            throw new IllegalStateException(
                    "Simulation wrote %d into its %s of %d floats".formatted(written, part, expected));
        }
    }

    /** buffer for a message with the header written, positioned at the payload */
    private static ByteBuffer header(MessageType messageType, long tick, double time, int payloadLength) {
        return ByteBuffer.allocate(HEADER_SIZE + payloadLength)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) messageType.id())
                // the three reserved bytes stay 0, as in every new buffer
                .position(4)
                .putInt((int) tick)
                .putFloat((float) time)
                .putInt(payloadLength);
    }

    /** names the kind of every object of a scene in its JSON, like {@code "kind": "Mesh"} */
    @JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "kind")
    private interface SceneObjectMixin {}
}
