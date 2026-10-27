package dev.simlab.server.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.simlab.core.InvalidInputException;
import dev.simlab.core.simulations.lorenz.LorenzDefinition;
import dev.simlab.server.FakeSimulation;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

@WebMvcTest(RoomController.class)
@MockitoBean(types = RoomService.class)
class RoomControllerTests {

    private final MockMvcTester mvc;
    private final RoomService rooms;

    RoomControllerTests(@Autowired MockMvcTester mvc, @Autowired RoomService rooms) {
        this.mvc = mvc;
        this.rooms = rooms;
    }

    @Test
    void opensARoomWithTheDefaultParameters() {
        SimulationRoom room = room();
        given(rooms.open(any(), anyMap())).willReturn(room);

        assertThat(open("{\"type\": \"lorenz\"}"))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader(HttpHeaders.LOCATION, "http://localhost/api/rooms/" + room.id())
                .bodyJson()
                .isStrictlyEqualTo("{\"id\": \"%s\", \"type\": \"lorenz\"}".formatted(room.id()));
        verify(rooms).open("lorenz", Map.of());
    }

    @Test
    void opensARoomWithTheGivenParameters() {
        given(rooms.open(any(), anyMap())).willReturn(room());

        assertThat(open("{\"type\": \"lorenz\", \"parameters\": {\"rho\": 20, \"beta\": 1}}"))
                .hasStatus(HttpStatus.CREATED);
        verify(rooms).open("lorenz", Map.of("rho", 20.0, "beta", 1.0));
    }

    @Test
    void rejectsRequestsThatDoNotFitTheSimulation() {
        given(rooms.open(any(), anyMap()))
                .willThrow(new InvalidInputException("Parameter 'rho' must lie between 0.0 and 60.0"));

        assertThat(open("{\"type\": \"lorenz\", \"parameters\": {\"rho\": 61}}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Parameter 'rho' must lie between 0.0 and 60.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"type\": \" \"}", "{\"type\": \"lorenz\", \"parameters\": {\"rho\": null}}"})
    void rejectsIncompleteRequests(String request) {
        assertThat(open(request)).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(rooms);
    }

    @Test
    void returnsOpenRooms() {
        SimulationRoom room = room();
        given(rooms.find(room.id())).willReturn(Optional.of(room));

        assertThat(mvc.get().uri("/api/rooms/{id}", room.id()))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("{\"id\": \"%s\", \"type\": \"lorenz\"}".formatted(room.id()));
    }

    @Test
    void answersNotFoundForUnknownRooms() {
        UUID id = UUID.randomUUID();
        given(rooms.find(id)).willReturn(Optional.empty());

        assertThat(mvc.get().uri("/api/rooms/{id}", id))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("There is no room " + id);
    }

    private MockMvcRequestBuilder open(String request) {
        return mvc.post()
                .uri("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request);
    }

    private static SimulationRoom room() {
        return new SimulationRoom(new LorenzDefinition(), new FakeSimulation(), mock(), 30, closed -> {});
    }
}
