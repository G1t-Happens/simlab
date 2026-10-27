package dev.simlab.server.simulation;

import dev.simlab.core.SimulationDefinition;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {

    /** lower-case letters and digits with single hyphens between words, as in an address */
    private static final Pattern TYPE = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private final List<SimulationDefinition> definitions;

    /** @throws IllegalArgumentException if a type is malformed or two simulations share one */
    SimulationService(List<SimulationDefinition> definitions) {
        Set<String> types = new HashSet<>();
        for (SimulationDefinition definition : definitions) {
            String type = definition.type();
            if (!TYPE.matcher(type).matches()) {
                throw new IllegalArgumentException(
                        "Type '%s' of %s must be lower-case letters and digits, with single hyphens between words"
                                .formatted(type, definition.getClass().getName()));
            }
            if (!types.add(type)) {
                throw new IllegalArgumentException("There is more than one simulation of type '%s'".formatted(type));
            }
        }
        this.definitions = definitions.stream()
                .sorted(Comparator.comparing(SimulationDefinition::type))
                .toList();
    }

    /** every simulation, in the order of their types */
    public List<SimulationDefinition> all() {
        return definitions;
    }

    public Optional<SimulationDefinition> find(String type) {
        return definitions.stream()
                .filter(definition -> definition.type().equals(type))
                .findFirst();
    }
}
