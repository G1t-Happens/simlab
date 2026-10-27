package dev.simlab.server.websocket;

import dev.simlab.server.room.RoomService;
import dev.simlab.server.room.SimulationRoom;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriTemplate;

/** Resolves the room of a handshake and answers with 404 if there's no such room */
@Component
class RoomHandshakeInterceptor implements HandshakeInterceptor {

    static final String ROOM_ATTRIBUTE = "simlab.room";

    private static final UriTemplate ROOM_PATH = new UriTemplate("/ws/rooms/{id}");

    private final RoomService rooms;

    RoomHandshakeInterceptor(RoomService rooms) {
        this.rooms = rooms;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Map<String, Object> attributes) {
        Optional<SimulationRoom> room = roomId(request.getURI().getPath()).flatMap(rooms::find);
        if (room.isEmpty()) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }
        attributes.put(ROOM_ATTRIBUTE, room.get());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            @Nullable Exception exception) {}

    private static Optional<UUID> roomId(String path) {
        String id = ROOM_PATH.match(path).get("id");
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
