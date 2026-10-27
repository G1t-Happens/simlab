package dev.simlab.core;

import java.util.List;
import java.util.Map;

/** A parameter of a simulation whose value is a number: one from a range, or one of a few to choose from */
public sealed interface ParameterSpec {

    /** identifies the parameter in inputs */
    String name();

    /** short human-readable name, like a Greek letter */
    String label();

    double defaultValue();

    Effect effect();

    /** Whether the parameter can take the value, never true for NaN */
    boolean accepts(double value);

    default double valueIn(Map<String, Double> values) {
        Double value = values.get(name());
        return value == null ? defaultValue() : value;
    }

    /**
     * A parameter whose value can be any number between two bounds
     *
     * @param unit unit of the values like {@code m/s}, empty for dimensionless parameters
     */
    record Range(String name, String label, String unit, double min, double max, double defaultValue, Effect effect)
            implements ParameterSpec {

        public Range {
            requireNameAndLabel(name, label);
            if (!(Double.isFinite(min) && Double.isFinite(max) && min < max)) {
                throw new IllegalArgumentException("Range [%s, %s] of '%s' is invalid".formatted(min, max, name));
            }
            if (!(defaultValue >= min && defaultValue <= max)) {
                throw new IllegalArgumentException("Default value of '%s' is out of range".formatted(name));
            }
        }

        @Override
        public boolean accepts(double value) {
            return value >= min && value <= max;
        }
    }

    /**
     * A parameter whose value is one of a few numbers, each naming an option, like the order of a numerical
     * method
     *
     * @param options at least two, in the order to offer them
     */
    record Choice(String name, String label, List<Option> options, double defaultValue, Effect effect)
            implements ParameterSpec {

        public Choice {
            requireNameAndLabel(name, label);
            options = List.copyOf(options);
            if (options.size() < 2) {
                throw new IllegalArgumentException("'%s' needs at least two options to choose from".formatted(name));
            }
            for (int i = 1; i < options.size(); i++) {
                double value = options.get(i).value();
                if (options.subList(0, i).stream().anyMatch(earlier -> earlier.value() == value)) {
                    throw new IllegalArgumentException(
                            "'%s' has more than one option of the value %s".formatted(name, value));
                }
            }
            if (options.stream().noneMatch(option -> option.value() == defaultValue)) {
                throw new IllegalArgumentException("Default value of '%s' is no option".formatted(name));
            }
        }

        @Override
        public boolean accepts(double value) {
            return options.stream().anyMatch(option -> option.value() == value);
        }

        public record Option(double value, String label) {

            public Option {
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("The value of an option must be finite, but was " + value);
                }
                if (label.isBlank()) {
                    throw new IllegalArgumentException("An option needs a label");
                }
            }
        }
    }

    /** When a new value of a parameter takes effect */
    enum Effect {
        /** at once, while the simulation runs */
        LIVE,
        /** when the simulation starts over, e.g. for the arrangement it starts from */
        RESTART
    }

    private static void requireNameAndLabel(String name, String label) {
        if (name.isBlank() || label.isBlank()) {
            throw new IllegalArgumentException("Name and label must not be blank");
        }
    }
}
