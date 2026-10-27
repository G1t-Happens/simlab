package dev.simlab.server.lesson;

import static java.util.Objects.requireNonNull;

import dev.simlab.core.InvalidInputException;
import dev.simlab.core.SimulationDefinition;
import dev.simlab.core.scene.Readouts;
import dev.simlab.core.scene.Readouts.Readout;
import dev.simlab.server.lesson.Lesson.Block;
import dev.simlab.server.lesson.Lesson.Calculation;
import dev.simlab.server.lesson.Lesson.Code;
import dev.simlab.server.lesson.Lesson.Experiment;
import dev.simlab.server.lesson.Lesson.Origin;
import dev.simlab.server.lesson.Lesson.Text;
import dev.simlab.server.lesson.Symbols.Declaration;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the lessons of simulations from their Markdown, {@code lesson.md} next to the simulation's definition.
 *
 * <p>A lesson can start with a YAML head between two lines of three dashes, which declares the
 * {@linkplain Symbols symbols} its formulas mark. A line that holds nothing but a method reference in
 * backticks, between blank lines, shows the code of the method. YAML in a block fenced as {@code experiment}
 * sets up an experiment and TeX in a block fenced as {@code calculation} calculates with the live values of
 * the simulation, all other lines are text. Methods the text mentions in backticks have to exist as well and
 * experiments have to fit the parameters of the simulation, so no lesson falls behind the code
 */
final class LessonReader {

    private static final Pattern REFERENCE = Pattern.compile("`(" + Reference.PATTERN.pattern() + ")`");

    private static final String TOOLKIT = "dev.simlab.core.toolkit";

    private static final String HEAD_FENCE = "---";

    private static final String EXPERIMENT_FENCE = "```experiment";

    private static final String CALCULATION_FENCE = "```calculation";

    private static final String CLOSING_FENCE = "```";

    private final SourceCode sources = new SourceCode();
    private final SourceLinks links;

    LessonReader(SourceLinks links) {
        this.links = links;
    }

    /**
     * Reads the lesson of a simulation
     *
     * @throws IllegalStateException if the simulation has no lesson
     * @throws IllegalArgumentException if its lesson refers to methods that don't exist or that belong to
     *     neither the simulation nor the toolkit, or doesn't fit its symbols or the parameters and readouts of
     *     the simulation
     */
    Lesson read(SimulationDefinition definition) {
        Class<?> type = definition.getClass();
        try (InputStream lesson = type.getResourceAsStream("lesson.md")) {
            if (lesson == null) {
                throw new IllegalStateException("%s has no lesson.md in its package".formatted(type.getName()));
            }
            return read(new String(lesson.readAllBytes(), StandardCharsets.UTF_8), definition);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Reads a lesson from its Markdown
     *
     * @param definition the simulation whose lesson it is
     * @throws IllegalArgumentException if the lesson refers to methods that don't exist or that belong to
     *     neither the simulation nor the toolkit, or doesn't fit its symbols or the parameters and readouts of
     *     the simulation
     */
    Lesson read(String markdown, SimulationDefinition definition) {
        try {
            List<String> lines = markdown.lines().toList();
            int bodyStart = bodyStart(lines);
            List<Readout> readouts =
                    definition.scene().readouts().map(Readouts::readouts).orElse(List.of());
            var symbols = new Symbols(declarations(lines.subList(0, bodyStart)), definition.parameters(), readouts);
            List<Block> blocks = blocks(lines.subList(bodyStart, lines.size()), definition, symbols);
            symbols.check(blocks);
            return new Lesson(symbols.list(), blocks);
        } catch (IllegalArgumentException e) {
            String lesson = definition.getClass().getPackageName().replace('.', '/') + "/lesson.md";
            throw new IllegalArgumentException("%s is broken: %s".formatted(lesson, e.getMessage()), e);
        }
    }

    private static int bodyStart(List<String> lines) {
        if (lines.isEmpty() || !lines.getFirst().equals(HEAD_FENCE)) {
            return 0;
        }
        return closingLine(lines, 0, HEAD_FENCE, "Its head") + 1;
    }

    /** Reads the symbols the head declares, from its lines with the fences around them */
    private static Map<String, Declaration> declarations(List<String> head) {
        if (head.isEmpty()) {
            return Map.of();
        }
        return YamlReader.read(String.join("\n", head.subList(1, head.size() - 1)), Head.class)
                .symbols();
    }

    private List<Block> blocks(List<String> lines, SimulationDefinition definition, Symbols symbols) {
        String simulationPackage = definition.getClass().getPackageName();
        List<Block> blocks = new ArrayList<>();
        var text = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            Matcher reference = REFERENCE.matcher(lines.get(i));
            if (lines.get(i).equals(EXPERIMENT_FENCE)) {
                int closing = closingLine(lines, i, CLOSING_FENCE, "An experiment");
                addText(blocks, text);
                blocks.add(experiment(String.join("\n", lines.subList(i + 1, closing)), definition));
                // read on after the experiment
                i = closing;
            } else if (lines.get(i).equals(CALCULATION_FENCE)) {
                int closing = closingLine(lines, i, CLOSING_FENCE, "A calculation");
                addText(blocks, text);
                blocks.add(new Calculation(String.join("\n", lines.subList(i + 1, closing))));
                // read on after the calculation
                i = closing;
            } else if (reference.matches() && isBlank(lines, i - 1) && isBlank(lines, i + 1)) {
                addText(blocks, text);
                blocks.add(code(Reference.parse(reference.group(1)), simulationPackage, symbols));
            } else {
                text.append(lines.get(i)).append('\n');
                // a method the text mentions has to exist as well
                reference.reset();
                while (reference.find()) {
                    Reference mentioned = Reference.parse(reference.group(1));
                    origin(sources.method(mentioned), mentioned, simulationPackage);
                }
            }
        }
        addText(blocks, text);
        return blocks;
    }

    private Code code(Reference reference, String simulationPackage, Symbols symbols) {
        MethodSource method = sources.method(reference);
        return new Code(
                reference.toString(),
                method.path(),
                method.firstLine(),
                method.lastLine(),
                method.code(),
                origin(method, reference, simulationPackage),
                links.link(method.path(), method.firstLine(), method.lastLine()).orElse(null),
                symbols.occurrencesIn(method));
    }

    private static Origin origin(MethodSource method, Reference reference, String simulationPackage) {
        if (method.packageName().equals(simulationPackage)) {
            return Origin.SIMULATION;
        }
        if (method.packageName().startsWith(TOOLKIT + ".")) {
            return Origin.TOOLKIT;
        }
        throw new IllegalArgumentException("%s belongs to %s, neither to the simulation nor to the toolkit"
                .formatted(reference, method.packageName()));
    }

    private static Experiment experiment(String yaml, SimulationDefinition definition) {
        Experiment experiment = YamlReader.read(yaml, Experiment.class);
        try {
            definition.validateValues(experiment.values());
        } catch (InvalidInputException e) {
            throw new IllegalArgumentException("An experiment does not fit the simulation: " + e.getMessage(), e);
        }
        return experiment;
    }

    /**
     * Index of the line that closes a part of a lesson
     *
     * @param part the part, for the exception message
     * @throws IllegalArgumentException if no line closes the part
     */
    private static int closingLine(List<String> lines, int opening, String closing, String part) {
        int index = lines.subList(opening + 1, lines.size()).indexOf(closing);
        if (index < 0) {
            throw new IllegalArgumentException("%s lacks the line %s that closes it".formatted(part, closing));
        }
        return opening + 1 + index;
    }

    private static void addText(List<Block> blocks, StringBuilder text) {
        if (!text.toString().isBlank()) {
            blocks.add(new Text(text.toString().strip()));
        }
        text.setLength(0);
    }

    /** lines before the first one and after the last one count as blank */
    private static boolean isBlank(List<String> lines, int index) {
        return index < 0 || index >= lines.size() || lines.get(index).isBlank();
    }

    /** The YAML head of a lesson, with the symbols the formulas mark by their names */
    record Head(Map<String, Declaration> symbols) {

        // YAML may leave out the symbols
        Head {
            requireNonNull(symbols, "The head of a lesson declares its symbols");
        }
    }
}
