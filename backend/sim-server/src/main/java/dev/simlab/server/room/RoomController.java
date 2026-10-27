package dev.simlab.server.room;

import dev.simlab.core.InvalidInputException;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/rooms")
class RoomController {

    private final RoomService rooms;

    RoomController(RoomService rooms) {
        this.rooms = rooms;
    }

    /** Opens a room, which closes again unless a browser joins it soon */
    @PostMapping
    ResponseEntity<RoomResponse> open(@Valid @RequestBody OpenRoomRequest request) {
        SimulationRoom room = rooms.open(request.type(), Objects.requireNonNullElse(request.parameters(), Map.of()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(room.id())
                .toUri();
        return ResponseEntity.created(location).body(RoomResponse.from(room));
    }

    @GetMapping("/{id}")
    RoomResponse get(@PathVariable UUID id) {
        return rooms.find(id)
                .map(RoomResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no room " + id));
    }

    @ExceptionHandler
    ProblemDetail handleInvalidInput(InvalidInputException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
}
