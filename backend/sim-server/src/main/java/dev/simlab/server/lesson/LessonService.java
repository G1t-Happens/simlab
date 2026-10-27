package dev.simlab.server.lesson;

import dev.simlab.core.SimulationDefinition;
import dev.simlab.server.simulation.SimulationService;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.GitProperties;
import org.springframework.stereotype.Service;

/**
 * The lessons of the simulations, read once when the server starts. A simulation without a lesson, or with
 * one that refers to methods that don't exist, keeps the server from starting
 */
@Service
class LessonService {

    private final Map<String, Lesson> lessons;

    LessonService(SimulationService simulations, LessonProperties properties, ObjectProvider<GitProperties> git) {
        GitProperties build = git.getIfAvailable();
        var reader =
                new LessonReader(new SourceLinks(properties.repository(), build == null ? null : build.getCommitId()));
        lessons = simulations.all().stream()
                .collect(Collectors.toUnmodifiableMap(SimulationDefinition::type, reader::read));
    }

    Optional<Lesson> find(String type) {
        return Optional.ofNullable(lessons.get(type));
    }
}
