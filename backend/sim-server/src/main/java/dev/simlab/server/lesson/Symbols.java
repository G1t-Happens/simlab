package dev.simlab.server.lesson;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;

import dev.simlab.core.ParameterSpec;
import dev.simlab.core.scene.Readouts.Readout;
import dev.simlab.server.lesson.Lesson.Block;
import dev.simlab.server.lesson.Lesson.Calculation;
import dev.simlab.server.lesson.Lesson.Code;
import dev.simlab.server.lesson.Lesson.Experiment;
import dev.simlab.server.lesson.Lesson.Occurrence;
import dev.simlab.server.lesson.Lesson.Symbol;
import dev.simlab.server.lesson.Lesson.Text;
import dev.simlab.server.lesson.MethodSource.Identifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The symbols the formulas of a lesson mark, as the head of the lesson declares them: the parameter or the
 * readout of the simulation a symbol stands for, and the identifiers that hold it in the code.
 *
 * <p>A formula marks a symbol with MathJax's class command like {@code \class{sigma}{\sigma}}, so the formula
 * stays TeX that MathJax renders as well. Not thread-safe
 */
final class Symbols {

    /** a mark in a formula like {@code \class{sigma}{\sigma}}, the name of the symbol is its group */
    private static final Pattern MARK = Pattern.compile("\\\\class\\{([^{}]*)}");

    private static final Pattern NAME = Pattern.compile("[a-z][a-zA-Z0-9]*");

    private final Map<String, Declaration> declarations;

    /** name of the symbol every identifier holds, in the order of the head */
    private final Map<String, String> symbolOfIdentifier = new LinkedHashMap<>();

    private final Set<String> shownIdentifiers = new HashSet<>();

    /**
     * Checks the symbols the head of a lesson declares
     *
     * @param declarations the symbols by name, in the order of the head
     * @throws IllegalArgumentException if a name is malformed, a symbol stands for a parameter or a readout the
     *     simulation doesn't have, for both, or for nothing at all, or two symbols claim the same identifier
     */
    Symbols(Map<String, Declaration> declarations, List<ParameterSpec> parameters, List<Readout> readouts) {
        this.declarations = new LinkedHashMap<>(declarations);
        Set<String> parameterNames =
                parameters.stream().map(ParameterSpec::name).collect(toSet());
        Set<String> readoutNames = readouts.stream().map(Readout::name).collect(toSet());
        declarations.forEach((name, declaration) -> {
            check(name, declaration, parameterNames, readoutNames);
            for (String identifier : declaration.code()) {
                String other = symbolOfIdentifier.putIfAbsent(identifier, name);
                if (other != null) {
                    throw new IllegalArgumentException(
                            "Symbols %s and %s both claim the identifier %s".formatted(other, name, identifier));
                }
            }
        });
    }

    /** where the symbols occur in the code of a method, as the identifiers that hold them */
    List<Occurrence> occurrencesIn(MethodSource method) {
        List<Occurrence> occurrences = new ArrayList<>();
        for (Identifier identifier : method.identifiers()) {
            String symbol = symbolOfIdentifier.get(identifier.name());
            if (symbol != null) {
                shownIdentifiers.add(identifier.name());
                occurrences.add(new Occurrence(
                        symbol,
                        identifier.line(),
                        identifier.column(),
                        identifier.name().length()));
            }
        }
        return occurrences;
    }

    /**
     * Checks that the text of a lesson marks every symbol and no other, that its calculations only mark symbols
     * with values, and that its code shows every identifier that holds a symbol
     *
     * @param blocks already searched for {@linkplain #occurrencesIn occurrences}
     * @throws IllegalArgumentException if the text or the code of the lesson doesn't fit its symbols
     */
    void check(List<Block> blocks) {
        Set<String> marked = blocks.stream()
                .flatMap(Symbols::markdown)
                .flatMap(Symbols::marks)
                .collect(toCollection(LinkedHashSet::new));
        for (String name : marked) {
            if (!declarations.containsKey(name)) {
                throw new IllegalArgumentException(
                        "\\class{%s} marks a symbol that the head does not declare".formatted(name));
            }
        }
        for (String name : declarations.keySet()) {
            if (!marked.contains(name)) {
                throw new IllegalArgumentException("No formula marks the symbol %s".formatted(name));
            }
        }
        symbolOfIdentifier.forEach((identifier, symbol) -> {
            if (!shownIdentifiers.contains(identifier)) {
                throw new IllegalArgumentException(
                        "No code of the lesson shows %s, which holds the symbol %s".formatted(identifier, symbol));
            }
        });
        for (Block block : blocks) {
            if (block instanceof Calculation calculation) {
                checkValues(calculation);
            }
        }
    }

    List<Symbol> list() {
        return declarations.entrySet().stream()
                .map(declaration -> new Symbol(
                        declaration.getKey(),
                        declaration.getValue().parameter(),
                        declaration.getValue().readout()))
                .toList();
    }

    private static void check(
            String name, Declaration declaration, Set<String> parameterNames, Set<String> readoutNames) {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("'%s' is no name of a symbol such as sigma or k1".formatted(name));
        }
        String parameter = declaration.parameter();
        if (parameter != null && !parameterNames.contains(parameter)) {
            throw new IllegalArgumentException(
                    "Symbol %s stands for the parameter %s, which the simulation does not have"
                            .formatted(name, parameter));
        }
        String readout = declaration.readout();
        if (readout != null && !readoutNames.contains(readout)) {
            throw new IllegalArgumentException(
                    "Symbol %s stands for the readout %s, which the simulation does not have".formatted(name, readout));
        }
        if (parameter != null && readout != null) {
            throw new IllegalArgumentException(
                    "Symbol %s stands for both a parameter and a readout, whose values differ".formatted(name));
        }
        if (!hasValue(declaration) && declaration.code().isEmpty()) {
            throw new IllegalArgumentException(
                    "Symbol %s stands for neither a parameter nor a readout nor code".formatted(name));
        }
    }

    /** A calculation writes the values in place of the symbols, so it may only mark symbols with values */
    private void checkValues(Calculation calculation) {
        for (String name : marks(calculation.formula()).toList()) {
            // the head declares every symbol a formula marks, as checked before
            if (!hasValue(requireNonNull(declarations.get(name)))) {
                throw new IllegalArgumentException(
                        "A calculation marks the symbol %s, which stands for neither a parameter nor a readout"
                                .formatted(name));
            }
        }
    }

    private static boolean hasValue(Declaration declaration) {
        return declaration.parameter() != null || declaration.readout() != null;
    }

    private static Stream<String> markdown(Block block) {
        return switch (block) {
            case Text text -> Stream.of(text.markdown());
            case Experiment experiment -> Stream.of(experiment.question(), experiment.explanation());
            case Calculation calculation -> Stream.of(calculation.formula());
            case Code _ -> Stream.empty();
        };
    }

    private static Stream<String> marks(String text) {
        return MARK.matcher(text).results().map(mark -> mark.group(1));
    }

    /**
     * A symbol as the head of a lesson declares it in YAML
     *
     * @param parameter the parameter of the simulation the symbol stands for, {@code null} if none
     * @param readout the readout of the simulation the symbol stands for, {@code null} if none
     * @param code identifiers that hold the symbol in the code of the lesson, empty if none does
     */
    record Declaration(@Nullable String parameter, @Nullable String readout, List<String> code) {

        // YAML may leave out the code, which has to be written as [] if no identifier holds the symbol
        Declaration {
            requireNonNull(code, "A symbol needs the identifiers that hold it in the code, [] if there are none");
            code = List.copyOf(code);
        }
    }
}
