package dev.simlab.server.room;

import java.util.UUID;

/**
 * An open room, which browsers join through the WebSocket {@code /ws/rooms/{id}}
 *
 * @param type the simulation that runs in the room
 */
record RoomResponse(UUID id, String type) {

    static RoomResponse from(SimulationRoom room) {
        return new RoomResponse(room.id(), room.definition().type());
    }
}
