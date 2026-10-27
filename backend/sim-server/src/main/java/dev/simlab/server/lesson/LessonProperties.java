package dev.simlab.server.lesson;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lesson settings
 *
 * @param repository the GitHub repository the server is built from, like
 *     {@code https://github.com/G1t-Happens/simlab}. The code in the lessons links to its lines
 */
@ConfigurationProperties("simlab.lessons")
record LessonProperties(@Nullable URI repository) {}
