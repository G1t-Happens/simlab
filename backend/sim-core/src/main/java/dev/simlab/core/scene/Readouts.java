package dev.simlab.core.scene;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Numbers the simulation reports with every frame, like the coordinates of a selected particle. The browser
 * lists them beside the scene and the lesson of the simulation puts them into its formulas.
 *
 * <p>A frame holds the value of one readout after another. Unknown values (e.g. those of the selected point
 * while there's none) are NaN
 *
 * @param readouts at least one, in the order of their values in frames
 */
public record Readouts(List<Readout> readouts) implements SceneObject {

    public Readouts {
        readouts = List.copyOf(readouts);
        if (readouts.isEmpty()) {
            throw new IllegalArgumentException("Readouts need at least one readout");
        }
        Set<String> names = new HashSet<>();
        for (Readout readout : readouts) {
            if (!names.add(readout.name())) {
                throw new IllegalArgumentException(
                        "There is more than one readout named '%s'".formatted(readout.name()));
            }
        }
    }

    @Override
    public int frameFloats() {
        return readouts.size();
    }

    /**
     * A number the simulation reports with every frame
     *
     * @param name identifies the readout, like {@code xDot}. Lessons tie their symbols to readouts by name
     * @param label short human-readable name, like a symbol
     * @param unit unit of the values like {@code m/s}, empty for dimensionless readouts
     */
    public record Readout(String name, String label, String unit) {

        public Readout {
            if (name.isBlank() || label.isBlank()) {
                throw new IllegalArgumentException("Name and label must not be blank");
            }
        }
    }
}
