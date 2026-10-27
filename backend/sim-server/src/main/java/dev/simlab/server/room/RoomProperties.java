package dev.simlab.server.room;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the simulation rooms
 *
 * @param tickRate simulation steps and frames per second
 * @param joinTimeout time after which a room nobody joined gets closed
 */
@ConfigurationProperties("simlab.rooms")
@Validated
public record RoomProperties(
        @DefaultValue("30") @Min(1) @Max(120) int tickRate,
        @DefaultValue("30s") @DurationMin(seconds = 1) Duration joinTimeout) {}
