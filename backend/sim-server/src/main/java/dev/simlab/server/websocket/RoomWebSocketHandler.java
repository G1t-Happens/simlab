package dev.simlab.server.websocket;

import dev.simlab.core.InvalidInputException;
import dev.simlab.server.protocol.ClientMessage;
import dev.simlab.server.protocol.ClientMessage.InputMessage;
import dev.simlab.server.protocol.ClientMessage.PauseMessage;
import dev.simlab.server.protocol.ClientMessage.PlayMessage;
import dev.simlab.server.protocol.ClientMessage.SpeedMessage;
import dev.simlab.server.protocol.ClientMessage.StepMessage;
import dev.simlab.server.protocol.ClientMessage.XrayMessage;
import dev.simlab.server.room.SimulationRoom;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator.OverflowStrategy;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lets a browser watch a room: the session joins the room, gets its frames and sends inputs, the settings of
 * the room's clock and whether the frames show the x-ray as JSON. Invalid messages close the session with
 * status 1007, binary messages aren't accepted
 */
@Component
class RoomWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(RoomWebSocketHandler.class);

    private static final String VIEWER_ATTRIBUTE = "simlab.viewer";

    /** a session that falls behind by this many bytes drops its oldest frames */
    private static final int SEND_BUFFER_LIMIT = 512 * 1024;

    /** a session that takes longer than this to send one message gets closed */
    private static final int SEND_TIME_LIMIT_MILLIS = 5_000;

    private final JsonMapper jsonMapper;

    RoomWebSocketHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        var viewer = new WebSocketViewer(new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MILLIS, SEND_BUFFER_LIMIT, OverflowStrategy.DROP));
        session.getAttributes().put(VIEWER_ATTRIBUTE, viewer);
        if (!room(session).join(viewer)) {
            session.close(CloseStatus.GOING_AWAY.withReason("Room closed"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        SimulationRoom room = room(session);
        try {
            switch (jsonMapper.readValue(message.getPayload(), ClientMessage.class)) {
                // the JSON null reads as no message at all
                case null -> throw new InvalidInputException("A message must be a JSON object");
                case InputMessage input -> room.submit(viewer(session), input.toInput(room.definition()));
                case PauseMessage _ -> room.pause();
                case PlayMessage _ -> room.play();
                case StepMessage _ -> room.step();
                case SpeedMessage speed -> room.setSpeed(speed.speed());
                case XrayMessage xray -> room.showXray(xray.xrayShown());
            }
        } catch (JacksonException | InvalidInputException e) {
            logger.debug("Closing session {} after an invalid message", session.getId(), e);
            session.close(CloseStatus.BAD_DATA.withReason("Invalid message"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (session.getAttributes().get(VIEWER_ATTRIBUTE) instanceof WebSocketViewer viewer) {
            room(session).leave(viewer);
        }
    }

    private static SimulationRoom room(WebSocketSession session) {
        if (session.getAttributes().get(RoomHandshakeInterceptor.ROOM_ATTRIBUTE) instanceof SimulationRoom room) {
            return room;
        }
        throw new IllegalStateException("Session %s has no room".formatted(session.getId()));
    }

    private static WebSocketViewer viewer(WebSocketSession session) {
        if (session.getAttributes().get(VIEWER_ATTRIBUTE) instanceof WebSocketViewer viewer) {
            return viewer;
        }
        throw new IllegalStateException("Session %s has no viewer".formatted(session.getId()));
    }
}
