package dev.simlab.server.room;

import dev.simlab.core.InvalidInputException;
import dev.simlab.core.SimulationDefinition;
import dev.simlab.server.simulation.SimulationService;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Opens rooms for the simulations the server offers, and closes them all when the server shuts down */
@Service
public class RoomService {

    private static final Logger logger = LoggerFactory.getLogger(RoomService.class);

    private final SimulationService simulations;
    private final RoomRepository rooms;
    private final ScheduledExecutorService scheduler;
    private final RoomProperties properties;

    RoomService(
            SimulationService simulations,
            RoomRepository rooms,
            ScheduledExecutorService roomScheduler,
            RoomProperties properties) {
        this.simulations = simulations;
        this.rooms = rooms;
        this.scheduler = roomScheduler;
        this.properties = properties;
    }

    /**
     * Opens a room for a simulation, which closes again unless someone joins it in time
     *
     * @param type the simulation, like {@code lorenz}
     * @param values replace the defaults of parameters, by name
     * @throws InvalidInputException if the server offers no simulation of the type, or the values don't fit it
     */
    public SimulationRoom open(String type, Map<String, Double> values) {
        SimulationDefinition definition = simulations
                .find(type)
                .orElseThrow(() -> new InvalidInputException("Unknown simulation type '%s'".formatted(type)));
        var room = new SimulationRoom(
                definition,
                definition.create(definition.validateValues(values)),
                scheduler,
                properties.tickRate(),
                rooms::remove);
        rooms.add(room);
        room.closeUnlessJoinedWithin(properties.joinTimeout());
        logger.info("Opened room {} for simulation {}", room.id(), type);
        return room;
    }

    /** the room with the given id, while it's open */
    public Optional<SimulationRoom> find(UUID id) {
        return rooms.find(id);
    }

    @PreDestroy
    void closeAll() {
        rooms.all().forEach(room -> room.close(CloseReason.SHUTDOWN));
    }
}
