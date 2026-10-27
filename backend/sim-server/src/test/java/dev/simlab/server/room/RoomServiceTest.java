package dev.simlab.server.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.simlab.core.InvalidInputException;
import dev.simlab.core.simulations.lorenz.LorenzDefinition;
import dev.simlab.server.simulation.SimulationService;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoomServiceTest {

    private static final Duration JOIN_TIMEOUT = Duration.ofSeconds(10);

    private final ScheduledExecutorService scheduler = mock();
    private final SimulationService simulations = mock();
    private final LorenzDefinition lorenz = spy(new LorenzDefinition());
    private final RoomService rooms =
            new RoomService(simulations, new RoomRepository(), scheduler, new RoomProperties(30, JOIN_TIMEOUT));

    @BeforeEach
    void offerLorenz() {
        given(simulations.find("lorenz")).willReturn(Optional.of(lorenz));
    }

    @Test
    void opensARoomForTheSimulationOfTheTypeAndFindsIt() {
        SimulationRoom room = rooms.open("lorenz", Map.of());

        assertThat(room.definition()).isSameAs(lorenz);
        assertThat(rooms.find(room.id())).contains(room);
    }

    @Test
    void createsTheSimulationWithTheGivenValuesOfItsParameters() {
        rooms.open("lorenz", Map.of("rho", 20.0));

        verify(lorenz).create(Map.of("rho", 20.0));
    }

    @Test
    void rejectsTypesOfSimulationsThatTheServerDoesNotOffer() {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> rooms.open("quantum", Map.of()))
                .withMessage("Unknown simulation type 'quantum'");
        verifyNoInteractions(scheduler);
    }

    @Test
    void rejectsValuesThatDoNotFitTheSimulation() {
        assertThatExceptionOfType(InvalidInputException.class)
                .isThrownBy(() -> rooms.open("lorenz", Map.of("rho", 61.0)))
                .withMessage("Parameter 'rho' must lie between 0.0 and 60.0");
        verify(lorenz, never()).create(anyMap());
        verifyNoInteractions(scheduler);
    }

    @Test
    void closesRoomsThatNobodyJoinsInTime() {
        rooms.open("lorenz", Map.of());

        verify(scheduler).schedule(any(Runnable.class), eq(JOIN_TIMEOUT.toNanos()), eq(TimeUnit.NANOSECONDS));
    }

    @Test
    void forgetsClosedRooms() {
        SimulationRoom room = rooms.open("lorenz", Map.of());

        room.close(CloseReason.ABANDONED);

        assertThat(rooms.find(room.id())).isEmpty();
    }

    @Test
    void closesEveryRoomOnShutdown() {
        SimulationRoom first = rooms.open("lorenz", Map.of());
        SimulationRoom second = rooms.open("lorenz", Map.of());

        rooms.closeAll();

        assertThat(rooms.find(first.id())).isEmpty();
        assertThat(rooms.find(second.id())).isEmpty();
    }
}
