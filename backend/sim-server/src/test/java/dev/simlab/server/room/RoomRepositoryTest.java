package dev.simlab.server.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.simlab.server.FakeDefinition;
import dev.simlab.server.FakeSimulation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoomRepositoryTest {

    private final RoomRepository rooms = new RoomRepository();
    private final SimulationRoom first = room();
    private final SimulationRoom second = room();

    @Test
    void findsTheRoomsThatItHolds() {
        rooms.add(first);
        rooms.add(second);

        assertThat(rooms.find(first.id())).contains(first);
        assertThat(rooms.find(UUID.randomUUID())).isEmpty();
        assertThat(rooms.all()).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void forgetsTheRoomsThatItRemoves() {
        rooms.add(first);
        rooms.add(second);

        rooms.remove(first);

        assertThat(rooms.find(first.id())).isEmpty();
        assertThat(rooms.all()).containsExactly(second);
    }

    @Test
    void listsTheRoomsOfTheMomentInAListOfItsOwn() {
        rooms.add(first);
        List<SimulationRoom> open = rooms.all();

        rooms.remove(first);

        assertThat(open).containsExactly(first);
    }

    private static SimulationRoom room() {
        var simulation = new FakeSimulation();
        return new SimulationRoom(new FakeDefinition(simulation, 1), simulation, mock(), 30, closed -> {});
    }
}
