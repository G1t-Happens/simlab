package dev.simlab.server.room;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** The open rooms, any thread can add, find and remove them */
@Repository
class RoomRepository {

    private final Map<UUID, SimulationRoom> rooms = new ConcurrentHashMap<>();

    void add(SimulationRoom room) {
        rooms.put(room.id(), room);
    }

    Optional<SimulationRoom> find(UUID id) {
        return Optional.ofNullable(rooms.get(id));
    }

    /** the open rooms in a list that closing them doesn't change */
    List<SimulationRoom> all() {
        return List.copyOf(rooms.values());
    }

    void remove(SimulationRoom room) {
        rooms.remove(room.id(), room);
    }
}
