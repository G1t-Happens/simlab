package dev.simlab.server.room;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Request to open a room
 *
 * @param type the simulation, e.g. {@code lorenz}
 * @param parameters values that replace the defaults of parameters, by name
 */
record OpenRoomRequest(@NotBlank String type, @Nullable Map<String, @NotNull Double> parameters) {}
