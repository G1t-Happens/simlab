package dev.simlab.server.websocket;

import dev.simlab.server.room.CloseReason;
import dev.simlab.server.room.Viewer;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/** A browser watching a room through a WebSocket session that's safe for concurrent sending */
final class WebSocketViewer implements Viewer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketViewer.class);

    private final WebSocketSession session;

    WebSocketViewer(WebSocketSession session) {
        this.session = session;
    }

    @Override
    public void send(ByteBuffer message) {
        try {
            session.sendMessage(new BinaryMessage(message));
        } catch (IOException | RuntimeException e) {
            // the session is closing, its close callback removes the viewer from the room
            logger.debug("Could not send a message to session {}", session.getId(), e);
        }
    }

    @Override
    public void disconnect(CloseReason reason) {
        CloseStatus status = switch (reason) {
            case FAILED -> CloseStatus.SERVER_ERROR;
            case SHUTDOWN -> CloseStatus.GOING_AWAY;
            case ABANDONED, NOT_JOINED -> CloseStatus.NORMAL;
        };
        try {
            session.close(status);
        } catch (IOException e) {
            logger.debug("Could not close session {}", session.getId(), e);
        }
    }
}
