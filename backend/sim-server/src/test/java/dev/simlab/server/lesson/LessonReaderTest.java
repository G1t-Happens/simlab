package dev.simlab.server.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.entry;

import dev.simlab.core.simulations.lorenz.LorenzDefinition;
import dev.simlab.server.FakeDefinition;
import dev.simlab.server.FakeSimulation;
import dev.simlab.server.lesson.Lesson.Calculation;
import dev.simlab.server.lesson.Lesson.Code;
import dev.simlab.server.lesson.Lesson.Experiment;
import dev.simlab.server.lesson.Lesson.Occurrence;
import dev.simlab.server.lesson.Lesson.Origin;
import dev.simlab.server.lesson.Lesson.Symbol;
import dev.simlab.server.lesson.Lesson.Text;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LessonReaderTest {

    private static final ExampleDefinition EXAMPLE = new ExampleDefinition();

    private final LessonReader reader = new LessonReader(new SourceLinks(null, null));

    @Test
    void showsTheCodeOfALineThatHoldsNothingButAReference() {
        Lesson lesson = reader.read("""
                ## A method

                It does nothing.

                `Methods#bare`

                Nothing at all.
                """, EXAMPLE);

        assertThat(lesson.blocks())
                .containsExactly(
                        new Text("## A method\n\nIt does nothing."),
                        new Code(
                                "Methods#bare",
                                "dev/simlab/server/lesson/Methods.java",
                                17,
                                17,
                                "    void bare() {}",
                                Origin.SIMULATION,
                                null,
                                List.of()),
                        new Text("Nothing at all."));
    }

    @Test
    void marksTheCodeOfTheToolkit() {
        Lesson lesson = reader.read("`RungeKutta4#step`", new LorenzDefinition());

        assertThat(lesson.blocks())
                .singleElement()
                .isInstanceOfSatisfying(
                        Code.class, code -> assertThat(code.origin()).isEqualTo(Origin.TOOLKIT));
    }

    @Test
    void keepsReferencesWithinTextAsText() {
        String markdown = """
                `Methods#bare` does nothing,
                `Methods#commented`
                neither.
                """;

        assertThat(reader.read(markdown, EXAMPLE).blocks()).containsExactly(new Text(markdown.strip()));
    }

    @Test
    void rejectsTextThatMentionsMethodsThatDoNotExist() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> reader.read("Unlike `Methods#gone`, it does nothing.", EXAMPLE))
                .withMessage("dev/simlab/server/lesson/lesson.md is broken: sim-core has no method Methods#gone");
    }

    @Test
    void rejectsCodeOfOtherSimulations() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> reader.read("`ClothSimulation#integrate`", new LorenzDefinition()))
                .withMessage(
                        "dev/simlab/core/simulations/lorenz/lesson.md is broken: ClothSimulation#integrate"
                                + " belongs to dev.simlab.core.simulations.cloth, neither to the simulation nor to the toolkit");
    }

    @Test
    void linksTheCodeToItsLinesOnGitHub() {
        var linking = new LessonReader(new SourceLinks(URI.create("https://github.com/G1t-Happens/simlab"), "f00d"));

        Lesson lesson = linking.read("`Methods#bare`", EXAMPLE);

        assertThat(lesson.blocks())
                .singleElement()
                .isInstanceOfSatisfying(
                        Code.class,
                        code -> assertThat(code.url())
                                .isEqualTo(URI.create(
                                        "https://github.com/G1t-Happens/simlab/blob/f00d/backend/sim-core/src/main/java"
                                                + "/dev/simlab/server/lesson/Methods.java#L17-L17")));
    }

    @Test
    void readsTheSymbolsOfItsHeadAndFindsTheirIdentifiersInTheCode() {
        Lesson lesson = reader.read("""
                ---
                symbols:
                  rate: { parameter: rate, code: [rate] }
                  dt: { code: [dt] }
                ---
                The amount decays as $e^{-\\class{rate}{\\lambda}\\, \\class{dt}{\\Delta t}}$ in a step:

                `Methods#decayed`
                """, EXAMPLE);

        assertThat(lesson.symbols()).containsExactly(new Symbol("rate", "rate", null), new Symbol("dt", null, null));
        assertThat(lesson.blocks())
                .last()
                .isInstanceOfSatisfying(
                        Code.class,
                        code -> assertThat(code.occurrences())
                                .containsExactly(
                                        new Occurrence("rate", 24, 42, 4),
                                        new Occurrence("dt", 24, 55, 2),
                                        new Occurrence("rate", 26, 35, 4),
                                        new Occurrence("dt", 26, 42, 2)));
    }

    @Test
    void readsLessonsWithoutHeadAsLessonsWithoutSymbols() {
        assertThat(reader.read("`Methods#decayed`", EXAMPLE).symbols()).isEmpty();
    }

    @Test
    void rejectsTextThatDoesNotFitTheSymbolsOfItsHead() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> reader.read("""
                        ---
                        symbols:
                          rate: { parameter: rate, code: [] }
                        ---
                        The amount decays at the rate $\\class{speed}{\\lambda}$.
                        """, EXAMPLE))
                .withMessage("dev/simlab/server/lesson/lesson.md is broken:"
                        + " \\class{speed} marks a symbol that the head does not declare");
    }

    @Test
    void rejectsAHeadWithoutTheLineThatClosesIt() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> reader.read("---\nsymbols: {}\n", EXAMPLE))
                .withMessage(
                        "dev/simlab/server/lesson/lesson.md is broken: Its head lacks the line --- that closes it");
    }

    @Test
    void setsUpExperimentsWhereTheTextHoldsThem() {
        Lesson lesson = reader.read("""
                What happens to the amount if it decays faster?

                ```experiment
                question: Does it vanish sooner?
                values:
                  rate: 5
                  amount: 50
                explanation: >-
                  It does, as
                  it decays faster.
                ```

                The rate sets how fast.
                """, EXAMPLE);

        assertThat(lesson.blocks())
                .containsExactly(
                        new Text("What happens to the amount if it decays faster?"),
                        new Experiment(
                                "Does it vanish sooner?",
                                Map.of("rate", 5.0, "amount", 50.0),
                                "It does, as it decays faster."),
                        new Text("The rate sets how fast."));
        assertThat(((Experiment) lesson.blocks().get(1)).values())
                .containsExactly(entry("rate", 5.0), entry("amount", 50.0));
    }

    @Test
    void acceptsSymbolsThatOnlyExperimentsMark() {
        Lesson lesson = reader.read("""
                ---
                symbols:
                  rate: { parameter: rate, code: [] }
                ---
                ```experiment
                question: What happens at $\\class{rate}{\\lambda} = 0$?
                values: { rate: 0 }
                explanation: Nothing decays.
                ```
                """, EXAMPLE);

        assertThat(lesson.symbols()).containsExactly(new Symbol("rate", "rate", null));
    }

    @Test
    void rejectsExperimentsWithValuesOutsideTheRangesOfTheParameters() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> reader.read(experiment("{ rate: 11 }"), EXAMPLE))
                .withMessage("dev/simlab/server/lesson/lesson.md is broken: An experiment does not fit the"
                        + " simulation: Parameter 'rate' must lie between 0.0 and 10.0");
    }

    @Test
    void rejectsExperimentsWithParametersThatTheSimulationDoesNotHave() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> reader.read(experiment("{ speed: 1 }"), EXAMPLE))
                .withMessage("dev/simlab/server/lesson/lesson.md is broken: An experiment does not fit the"
                        + " simulation: Unknown parameter 'speed'");
    }

    @Test
    void rejectsExperimentsThatLeaveOutAPart() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> reader.read("```experiment\nquestion: Why?\nvalues: {}\n```", EXAMPLE))
                .withMessageStartingWith("dev/simlab/server/lesson/lesson.md is broken: ")
                .withMessageContaining("An experiment needs an explanation");
    }

    @Test
    void rejectsExperimentsWithoutTheLineThatClosesThem() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> reader.read("```experiment\nquestion: Why?\n", EXAMPLE))
                .withMessage("dev/simlab/server/lesson/lesson.md is broken:"
                        + " An experiment lacks the line ``` that closes it");
    }

    @Test
    void calculatesWithTheValuesOfParametersAndReadoutsWhereTheTextHoldsTheCalculation() {
        Lesson lesson = reader.read("""
                ---
                symbols:
                  rate: { parameter: rate, code: [] }
                  remaining: { readout: remaining, code: [] }
                ---
                What remains of the amount now?

                ```calculation
                A\\, e^{-\\class{rate}{\\lambda} t}
                  = \\class{remaining}{A(t)}
                ```

                Less and less.
                """, EXAMPLE);

        assertThat(lesson.symbols())
                .containsExactly(new Symbol("rate", "rate", null), new Symbol("remaining", null, "remaining"));
        assertThat(lesson.blocks())
                .containsExactly(
                        new Text("What remains of the amount now?"),
                        new Calculation("A\\, e^{-\\class{rate}{\\lambda} t}\n  = \\class{remaining}{A(t)}"),
                        new Text("Less and less."));
    }

    @Test
    void rejectsSymbolsOfReadoutsThatTheSimulationDoesNotHave() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> reader.read("""
                        ---
                        symbols:
                          left: { readout: left, code: [] }
                        ---
                        ```calculation
                        \\class{left}{A(t)}
                        ```
                        """, EXAMPLE))
                .withMessage("dev/simlab/server/lesson/lesson.md is broken:"
                        + " Symbol left stands for the readout left, which the simulation does not have");
    }

    @Test
    void rejectsCalculationsWithoutAFormula() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> reader.read("```calculation\n\n```", EXAMPLE))
                .withMessage("dev/simlab/server/lesson/lesson.md is broken: A calculation needs a formula");
    }

    @Test
    void rejectsCalculationsWithoutTheLineThatClosesThem() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> reader.read("```calculation\nA(t)\n", EXAMPLE))
                .withMessage("dev/simlab/server/lesson/lesson.md is broken:"
                        + " A calculation lacks the line ``` that closes it");
    }

    @Test
    void readsTheLessonNextToTheDefinitionOfASimulation() {
        Lesson lesson = reader.read(new LorenzDefinition());

        assertThat(lesson.blocks()).first().isInstanceOf(Text.class);
        assertThat(lesson.blocks()).hasAtLeastOneElementOfType(Code.class);
    }

    @Test
    void rejectsSimulationsWithoutLesson() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> reader.read(new FakeDefinition(new FakeSimulation(), 1)))
                .withMessage("dev.simlab.server.FakeDefinition has no lesson.md in its package");
    }

    /** a lesson with nothing but an experiment with the given values, in YAML */
    private static String experiment(String values) {
        return """
                ```experiment
                question: What happens?
                values: %s
                explanation: It changes.
                ```
                """.formatted(values);
    }
}
