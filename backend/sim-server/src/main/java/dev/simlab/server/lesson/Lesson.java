package dev.simlab.server.lesson;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The lesson of a simulation: text with formulas, the code it explains, experiments with the simulation and
 * calculations with its live values
 *
 * @param symbols the symbols the formulas mark, which the code and the controls of the simulation show too
 * @param blocks text, code, experiments and calculations in the order of the lesson
 */
record Lesson(List<Symbol> symbols, List<Block> blocks) {

    Lesson {
        symbols = List.copyOf(symbols);
        blocks = List.copyOf(blocks);
    }

    /**
     * A symbol that formulas mark, like {@code \class{sigma}{\sigma}}
     *
     * @param name the name in the mark, like {@code sigma}
     * @param parameter the parameter of the simulation the symbol stands for, {@code null} if none
     * @param readout the readout of the simulation the symbol stands for, {@code null} if none
     */
    record Symbol(
            String name,
            @Nullable String parameter,
            @Nullable String readout) {}

    /** A part of a lesson, its JSON names its kind like {@code "kind": "Code"} */
    @JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "kind")
    sealed interface Block permits Text, Code, Experiment, Calculation {}

    /**
     * Markdown with formulas in TeX between {@code $} within a line and between {@code $$} on lines of their
     * own, as GitHub writes them
     */
    record Text(String markdown) implements Block {}

    /**
     * A method of sim-core, from the comment above it to its closing brace
     *
     * @param reference the method as the lesson refers to it, like {@code RungeKutta4#step}
     * @param path path of the source file below the source root, like
     *     {@code dev/simlab/core/toolkit/ode/RungeKutta4.java}
     * @param firstLine counted from 1
     * @param origin whether the method belongs to the simulation or to the toolkit
     * @param url the lines on GitHub at the commit the server was built from, {@code null} if that's unknown
     * @param occurrences where the symbols of the lesson occur in the code
     */
    record Code(
            String reference,
            String path,
            int firstLine,
            int lastLine,
            String code,
            Origin origin,
            @Nullable URI url,
            List<Occurrence> occurrences)
            implements Block {

        Code {
            occurrences = List.copyOf(occurrences);
        }
    }

    enum Origin {
        /** in the package of the simulation, which no other simulation uses */
        SIMULATION,
        /** in the toolkit, which every simulation can use */
        TOOLKIT
    }

    /**
     * An identifier in code that holds a symbol of the lesson
     *
     * @param line line in the file, counted from 1
     * @param column first character of the identifier in the line, counted from 1
     */
    record Occurrence(String symbol, int line, int column, int length) {}

    /**
     * An experiment with the simulation: a question to predict the answer to, the parameter values that answer
     * it, and why the simulation behaves as it does. Its text is Markdown with formulas like in {@link Text}
     *
     * @param values the parameter values the experiment sets, by name in the order of the lesson. The other
     *     parameters take their defaults
     */
    record Experiment(String question, Map<String, Double> values, String explanation) implements Block {

        // lessons write experiments in YAML, which may leave out any part
        Experiment {
            requireNonNull(question, "An experiment needs a question");
            requireNonNull(values, "An experiment needs values");
            requireNonNull(explanation, "An experiment needs an explanation");
            values.forEach((name, value) -> requireNonNull(value, () -> "An experiment sets no value for " + name));
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }

    /**
     * A formula with the live values of its symbols: the browser writes the current value of every symbol it
     * marks in place of the symbol (the value of a parameter or of a readout, like a coordinate of the
     * selected particle) as long as the value is known
     *
     * @param formula TeX, shown on lines of its own
     */
    record Calculation(String formula) implements Block {

        Calculation {
            if (formula.isBlank()) {
                throw new IllegalArgumentException("A calculation needs a formula");
            }
        }
    }
}
