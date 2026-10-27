package dev.simlab.server.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

import dev.simlab.server.protocol.MessageEncoder;
import dev.simlab.server.protocol.MessageType;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonContent;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/** Opens rooms through the HTTP API and watches them through the WebSocket, like the browser does */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class RoomWebSocketIntegrationTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /** vertices along each edge of the cloth */
    private static final int CLOTH_SIZE = 48;

    private static final int LORENZ_PARTICLES = 1_000;

    /** coordinates of the selected particle and their rates of change */
    private static final int LORENZ_READOUTS = 6;

    /** arrows of the Lorenz attractor's x-ray, at the points of a 9x9x9 grid */
    private static final int LORENZ_ARROWS = 729;

    private final RestTestClient http;
    private final int port;
    private final StandardWebSocketClient webSocketClient;

    RoomWebSocketIntegrationTests(@Autowired RestTestClient http, @LocalServerPort int port) {
        this.http = http;
        this.port = port;
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        // the scene and the frames of the cloth exceed the default buffer size of 8 KiB
        container.setDefaultMaxBinaryMessageBufferSize(256 * 1024);
        this.webSocketClient = new StandardWebSocketClient(container);
    }

    @Test
    void streamsTheSceneAndThenTheStateOfTheSimulation() throws Exception {
        var recorder = new SessionRecorder();
        WebSocketSession session = join(openRoom("{\"type\": \"lorenz\"}"), recorder);

        JsonContent scene = scene(recorder.nextMessage());
        ByteBuffer first = recorder.nextMessage();
        ByteBuffer second = recorder.nextMessage();

        int floats = 3 * LORENZ_PARTICLES + LORENZ_READOUTS;
        assertThat(scene).extractingPath("$.objects[*].kind").asArray().containsExactly("PointCloud", "Readouts");
        assertThat(scene).extractingPath("$.objects[0].count").isEqualTo(LORENZ_PARTICLES);
        assertThat(scene).extractingPath("$.objects[1].readouts").asArray().hasSize(LORENZ_READOUTS);
        assertThat(scene).extractingPath("$.xray[*].kind").asArray().containsExactly("Lines");
        assertThat(first.get(0)).as("message type").isEqualTo((byte) MessageType.FRAME.id());
        assertThat(first.getInt(12)).as("payload length").isEqualTo(floats * Float.BYTES);
        assertThat(first.remaining()).isEqualTo(MessageEncoder.HEADER_SIZE + floats * Float.BYTES);
        assertThat(second.getInt(4)).as("tick").isGreaterThan(first.getInt(4));
        assertThat(second.getFloat(8)).as("simulation time").isGreaterThan(first.getFloat(8));
        session.close();
    }

    @Test
    void sendsTheSceneOfTheClothBeforeItsFrames() throws Exception {
        var recorder = new SessionRecorder();
        WebSocketSession session = join(openRoom("{\"type\": \"cloth\"}"), recorder);

        JsonContent scene = scene(recorder.nextMessage());
        ByteBuffer frame = recorder.nextMessage();

        int vertices = CLOTH_SIZE * CLOTH_SIZE;
        int triangles = 2 * (CLOTH_SIZE - 1) * (CLOTH_SIZE - 1);
        assertThat(scene)
                .extractingPath("$.objects[*].kind")
                .asArray()
                .containsExactly("Mesh", "Sphere", "Floor", "Readouts");
        assertThat(scene).extractingPath("$.objects[0].vertexCount").isEqualTo(vertices);
        assertThat(scene).extractingPath("$.objects[0].triangles").asArray().hasSize(3 * triangles);
        assertThat(frame.get(0)).as("message type").isEqualTo((byte) MessageType.FRAME.id());
        assertThat(frame.getInt(12))
                .as("payload length of the coordinates, the sphere and the readouts of the wind")
                .isEqualTo((3 * vertices + 4 + 4) * Float.BYTES);
        session.close();
    }

    @Test
    void appliesParametersOfTheRequestAndOfMessages() throws Exception {
        var recorder = new SessionRecorder();
        WebSocketSession session = join(openRoom("{\"type\": \"lorenz\", \"parameters\": {\"rho\": 0}}"), recorder);

        await("particles coming to rest at the origin with rho = 0")
                .atMost(TIMEOUT)
                .until(() -> largestCoordinate(recorder.latestFrame()) < 2);

        session.sendMessage(new TextMessage("{\"type\": \"param\", \"name\": \"rho\", \"value\": 28}"));

        await("particles leaving the origin with rho = 28")
                .atMost(TIMEOUT)
                .until(() -> largestCoordinate(recorder.latestFrame()) > 5);
        session.close();
    }

    @Test
    void movesAGrabbedVertexWhereverItIsDragged() throws Exception {
        var recorder = new SessionRecorder();
        WebSocketSession session = join(openRoom("{\"type\": \"cloth\"}"), recorder);
        // middle of the free edge, opposite the pinned corners
        int vertex = CLOTH_SIZE * (CLOTH_SIZE - 1) + CLOTH_SIZE / 2;
        ByteBuffer frame = recorder.latestFrame();

        session.sendMessage(new TextMessage("{\"type\": \"grab\", \"vertex\": %d, \"x\": %s, \"y\": %s, \"z\": %s}"
                .formatted(vertex, x(frame, vertex), y(frame, vertex), z(frame, vertex))));
        session.sendMessage(new TextMessage(
                "{\"type\": \"drag\", \"vertex\": %d, \"x\": 0.5, \"y\": 3, \"z\": -1}".formatted(vertex)));

        await("vertex following the drag").atMost(TIMEOUT).untilAsserted(() -> {
            ByteBuffer latest = recorder.latestFrame();
            assertThat(new float[] {x(latest, vertex), y(latest, vertex), z(latest, vertex)})
                    .containsExactly(0.5f, 3, -1);
        });

        session.sendMessage(new TextMessage("{\"type\": \"release\", \"vertex\": %d}".formatted(vertex)));

        await("released vertex falling").atMost(TIMEOUT).until(() -> y(recorder.latestFrame(), vertex) < 2.5f);
        session.close();
    }

    @Test
    void reportsTheStateOfASelectedParticle() throws Exception {
        var recorder = new SessionRecorder();
        WebSocketSession session = join(openRoom("{\"type\": \"lorenz\"}"), recorder);

        assertThat(lorenzReadout(recorder.latestFrame(), 0))
                .as("x while no particle is selected")
                .isNaN();

        session.sendMessage(new TextMessage("{\"type\": \"select\", \"point\": 7}"));

        await("readouts of particle 7").atMost(TIMEOUT).untilAsserted(() -> {
            ByteBuffer frame = recorder.latestFrame();
            assertThat(new float[] {lorenzReadout(frame, 0), lorenzReadout(frame, 1), lorenzReadout(frame, 2)})
                    .containsExactly(x(frame, 7), y(frame, 7), z(frame, 7));
        });
        session.close();
    }

    @Test
    void pausesTheSimulationAndAdvancesItByATickPerStep() throws Exception {
        var recorder = new SessionRecorder();
        // the cloth advances by the time of a tick in every tick, whatever the size of its substeps
        WebSocketSession session = join(openRoom("{\"type\": \"cloth\"}"), recorder);
        float tick = timeOfATick(recorder);

        session.sendMessage(new TextMessage("{\"type\": \"pause\"}"));

        await("time standing still")
                .atMost(TIMEOUT)
                .until(() -> time(recorder.latestFrame()) == time(recorder.nextMessage()));
        float paused = time(recorder.latestFrame());

        session.sendMessage(new TextMessage("{\"type\": \"step\"}"));

        await("time advancing by a tick")
                .atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(time(recorder.latestFrame())).isCloseTo(paused + tick, within(1e-4f)));

        session.sendMessage(new TextMessage("{\"type\": \"play\"}"));

        await("time running again").atMost(TIMEOUT).until(() -> time(recorder.latestFrame()) > paused + 2 * tick);
        session.close();
    }

    @Test
    void playsTheSimulationInSlowMotion() throws Exception {
        var recorder = new SessionRecorder();
        WebSocketSession session = join(openRoom("{\"type\": \"cloth\"}"), recorder);
        float tick = timeOfATick(recorder);

        session.sendMessage(new TextMessage("{\"type\": \"speed\", \"value\": 0.25}"));

        await("a quarter of the time of a tick passing per tick")
                .atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(timeOfATick(recorder)).isCloseTo(tick / 4, within(1e-5f)));
        session.close();
    }

    @Test
    void showsTheXrayInTheFramesWhileAskedTo() throws Exception {
        var recorder = new SessionRecorder();
        WebSocketSession session = join(openRoom("{\"type\": \"lorenz\"}"), recorder);
        int frameBytes = (3 * LORENZ_PARTICLES + LORENZ_READOUTS) * Float.BYTES;
        int xrayBytes = (1 + 7 * LORENZ_ARROWS) * Float.BYTES;

        session.sendMessage(new TextMessage("{\"type\": \"xray\", \"shown\": true}"));

        await("frames with the arrows of the x-ray")
                .atMost(TIMEOUT)
                .until(() -> recorder.latestFrame().getInt(12) == frameBytes + xrayBytes);

        session.sendMessage(new TextMessage("{\"type\": \"xray\", \"shown\": false}"));

        await("frames without the x-ray")
                .atMost(TIMEOUT)
                .until(() -> recorder.latestFrame().getInt(12) == frameBytes);
        session.close();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "null",
                "{\"type\": \"param\", \"name\": \"gamma\", \"value\": 1}",
                "{\"type\": \"select\", \"point\": 1000}",
                "{\"type\": \"speed\", \"value\": 2}",
                "{\"type\": \"xray\"}"
            })
    void closesTheSessionAfterAnInvalidMessage(String message) throws Exception {
        var recorder = new SessionRecorder();
        WebSocketSession session = join(openRoom("{\"type\": \"lorenz\"}"), recorder);

        session.sendMessage(new TextMessage(message));

        assertThat(recorder.closeStatus().getCode()).isEqualTo(CloseStatus.BAD_DATA.getCode());
    }

    @Test
    void opensNoRoomsForSimulationsThatTheServerDoesNotOffer() {
        http.post()
                .uri("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"type\": \"quantum\"}")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("Unknown simulation type 'quantum'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000-0000-0000-0000-000000000000", "not-a-room"})
    void refusesToJoinRoomsThatDoNotExist(String id) {
        assertThat(webSocketClient.execute(new SessionRecorder(), "ws://localhost:{port}/ws/rooms/{id}", port, id))
                .failsWithin(TIMEOUT)
                .withThrowableOfType(ExecutionException.class)
                .withMessageContaining("404");
    }

    @Test
    void closesTheRoomWhenTheLastViewerLeaves() throws Exception {
        UUID roomId = openRoom("{\"type\": \"lorenz\"}");
        WebSocketSession session = join(roomId, new SessionRecorder());

        session.close();

        await().atMost(TIMEOUT)
                .untilAsserted(() -> http.get()
                        .uri("/api/rooms/{id}", roomId)
                        .exchange()
                        .expectStatus()
                        .isNotFound());
    }

    private UUID openRoom(String request) {
        Room room = http.post()
                .uri("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus()
                .isCreated()
                .returnResult(Room.class)
                .getResponseBody();
        assertThat(room).isNotNull();
        return room.id();
    }

    private WebSocketSession join(UUID roomId, SessionRecorder recorder) throws Exception {
        return webSocketClient
                .execute(recorder, "ws://localhost:{port}/ws/rooms/{id}", port, roomId)
                .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static JsonContent scene(ByteBuffer message) {
        assertThat(message.get(0)).as("message type").isEqualTo((byte) MessageType.SCENE.id());
        int payloadLength = message.getInt(12);
        assertThat(message.remaining()).isEqualTo(MessageEncoder.HEADER_SIZE + payloadLength);
        return new JsonContent(StandardCharsets.UTF_8
                .decode(message.slice(MessageEncoder.HEADER_SIZE, payloadLength))
                .toString());
    }

    private static double largestCoordinate(ByteBuffer frame) {
        double largest = 0;
        for (int coordinate = 0; coordinate < 3 * LORENZ_PARTICLES; coordinate++) {
            largest =
                    Math.max(largest, Math.abs(frame.getFloat(MessageEncoder.HEADER_SIZE + coordinate * Float.BYTES)));
        }
        return largest;
    }

    /** x of a vertex or a particle, whose coordinates come first in a frame */
    private static float x(ByteBuffer frame, int point) {
        return frame.getFloat(MessageEncoder.HEADER_SIZE + 3 * point * Float.BYTES);
    }

    private static float y(ByteBuffer frame, int point) {
        return frame.getFloat(MessageEncoder.HEADER_SIZE + (3 * point + 1) * Float.BYTES);
    }

    private static float z(ByteBuffer frame, int point) {
        return frame.getFloat(MessageEncoder.HEADER_SIZE + (3 * point + 2) * Float.BYTES);
    }

    /** a readout of the Lorenz attractor, its readouts follow the coordinates of its particles */
    private static float lorenzReadout(ByteBuffer frame, int readout) {
        return frame.getFloat(MessageEncoder.HEADER_SIZE + (3 * LORENZ_PARTICLES + readout) * Float.BYTES);
    }

    private static float time(ByteBuffer frame) {
        return frame.getFloat(8);
    }

    private static float timeOfATick(SessionRecorder recorder) throws InterruptedException {
        float before = time(recorder.latestFrame());
        return time(recorder.nextMessage()) - before;
    }

    private record Room(UUID id, String type) {}

    /** records the binary messages a session gets and the status it's closed with */
    private static final class SessionRecorder extends AbstractWebSocketHandler {

        private final BlockingQueue<ByteBuffer> messages = new LinkedBlockingQueue<>();
        private final CompletableFuture<CloseStatus> closeStatus = new CompletableFuture<>();

        @Override
        protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
            // the client may reuse the buffer once this method returns
            ByteBuffer payload = message.getPayload();
            messages.add(
                    ByteBuffer.allocate(payload.remaining()).put(payload).flip().order(ByteOrder.LITTLE_ENDIAN));
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closeStatus.complete(status);
        }

        ByteBuffer nextMessage() throws InterruptedException {
            ByteBuffer message = messages.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(message).as("message within %s", TIMEOUT).isNotNull();
            return message;
        }

        /** waits for a frame and skips older messages that arrived in the meantime */
        ByteBuffer latestFrame() throws InterruptedException {
            ByteBuffer latest = nextMessage();
            for (ByteBuffer newer = messages.poll(); newer != null; newer = messages.poll()) {
                latest = newer;
            }
            while (latest.get(0) != MessageType.FRAME.id()) {
                latest = nextMessage();
            }
            return latest;
        }

        CloseStatus closeStatus() throws Exception {
            return closeStatus.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        }
    }
}
