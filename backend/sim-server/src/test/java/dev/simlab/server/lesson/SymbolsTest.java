package dev.simlab.server.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import dev.simlab.core.ParameterSpec;
import dev.simlab.core.scene.Readouts;
import dev.simlab.core.scene.Readouts.Readout;
import dev.simlab.server.lesson.Lesson.Calculation;
import dev.simlab.server.lesson.Lesson.Experiment;
import dev.simlab.server.lesson.Lesson.Occurrence;
import dev.simlab.server.lesson.Lesson.Symbol;
import dev.simlab.server.lesson.Lesson.Text;
import dev.simlab.server.lesson.MethodSource.Identifier;
import dev.simlab.server.lesson.Symbols.Declaration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SymbolsTest {

    /** the parameters rate and amount */
    private static final List<ParameterSpec> PARAMETERS = new ExampleDefinition().parameters();

    /** the readout remaining */
    private static final List<Readout> READOUTS =
            new ExampleDefinition().scene().readouts().map(Readouts::readouts).orElseThrow();

    /** a method in which the identifier rate occurs twice and dt once */
    private static final MethodSource METHOD = new MethodSource(
            "dev.simlab.server.lesson",
            "dev/simlab/server/lesson/Methods.java",
            1,
            2,
            "double decayed = amount * Math.exp(-rate * dt);\nreturn decayed * rate;",
            List.of(new Identifier("rate", 1, 38), new Identifier("dt", 1, 45), new Identifier("rate", 2, 18)));

    private static final Text MARKING_RATE = new Text("It decays at the rate $\\class{rate}{\\lambda}$.");

    @Test
    void findsTheIdentifiersThatHoldTheSymbolsInTheCodeOfAMethod() {
        var symbols = symbols(Map.of("rate", new Declaration("rate", null, List.of("rate"))));

        assertThat(symbols.occurrencesIn(METHOD))
                .containsExactly(new Occurrence("rate", 1, 38, 4), new Occurrence("rate", 2, 18, 4));
    }

    @Test
    void listsTheSymbolsWithTheirParametersAndReadoutsInTheOrderOfTheHead() {
        var declarations = new LinkedHashMap<String, Declaration>();
        declarations.put("rate", new Declaration("rate", null, List.of()));
        declarations.put("remaining", new Declaration(null, "remaining", List.of()));
        declarations.put("dt", new Declaration(null, null, List.of("dt")));

        assertThat(symbols(declarations).list())
                .containsExactly(
                        new Symbol("rate", "rate", null),
                        new Symbol("remaining", null, "remaining"),
                        new Symbol("dt", null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Rate", "1rate", "decay_rate", "decay-rate"})
    void rejectsNamesThatAreNotInLowerCamelCase(String name) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> symbols(Map.of(name, new Declaration("rate", null, List.of()))))
                .withMessage("'%s' is no name of a symbol such as sigma or k1", name);
    }

    @Test
    void rejectsSymbolsOfParametersThatTheSimulationDoesNotHave() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> symbols(Map.of("speed", new Declaration("speed", null, List.of()))))
                .withMessage("Symbol speed stands for the parameter speed, which the simulation does not have");
    }

    @Test
    void rejectsSymbolsOfReadoutsThatTheSimulationDoesNotHave() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> symbols(Map.of("left", new Declaration(null, "left", List.of()))))
                .withMessage("Symbol left stands for the readout left, which the simulation does not have");
    }

    @Test
    void rejectsSymbolsOfBothAParameterAndAReadout() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> symbols(Map.of("amount", new Declaration("amount", "remaining", List.of()))))
                .withMessage("Symbol amount stands for both a parameter and a readout, whose values differ");
    }

    @Test
    void rejectsSymbolsThatStandForNothing() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> symbols(Map.of("lambda", new Declaration(null, null, List.of()))))
                .withMessage("Symbol lambda stands for neither a parameter nor a readout nor code");
    }

    @Test
    void rejectsIdentifiersThatTwoSymbolsClaim() {
        var declarations = new LinkedHashMap<String, Declaration>();
        declarations.put("rate", new Declaration("rate", null, List.of("rate")));
        declarations.put("lambda", new Declaration(null, null, List.of("rate")));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> symbols(declarations))
                .withMessage("Symbols rate and lambda both claim the identifier rate");
    }

    @Test
    void acceptsTextThatMarksEverySymbolAndCodeThatShowsEveryIdentifier() {
        var symbols = symbols(Map.of("rate", new Declaration("rate", null, List.of("rate"))));
        symbols.occurrencesIn(METHOD);

        assertThatNoException().isThrownBy(() -> symbols.check(List.of(MARKING_RATE)));
    }

    @Test
    void findsTheMarksInTheTextOfExperiments() {
        var symbols = symbols(Map.of("rate", new Declaration("rate", null, List.of())));
        var experiment =
                new Experiment("What if $\\class{rate}{\\lambda} = 0$?", Map.of("rate", 0.0), "Nothing decays.");

        assertThatNoException().isThrownBy(() -> symbols.check(List.of(experiment)));
    }

    @Test
    void acceptsCalculationsThatMarkSymbolsWithValuesOfParametersAndReadouts() {
        var declarations = new LinkedHashMap<String, Declaration>();
        declarations.put("amount", new Declaration("amount", null, List.of()));
        declarations.put("remaining", new Declaration(null, "remaining", List.of()));
        var symbols = symbols(declarations);
        var calculation = new Calculation("\\class{amount}{A}\\, e^{-\\lambda t} = \\class{remaining}{A(t)}");

        assertThatNoException().isThrownBy(() -> symbols.check(List.of(calculation)));
    }

    @Test
    void rejectsCalculationsThatMarkSymbolsWithoutValues() {
        var declarations = new LinkedHashMap<String, Declaration>();
        declarations.put("remaining", new Declaration(null, "remaining", List.of()));
        declarations.put("dt", new Declaration(null, null, List.of("dt")));
        var symbols = symbols(declarations);
        symbols.occurrencesIn(METHOD);
        var calculation = new Calculation("\\class{remaining}{A(t + \\class{dt}{\\Delta t})}");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> symbols.check(List.of(calculation)))
                .withMessage("A calculation marks the symbol dt, which stands for neither a parameter nor a readout");
    }

    @Test
    void rejectsMarksOfSymbolsThatTheHeadDoesNotDeclare() {
        var symbols = symbols(Map.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> symbols.check(List.of(MARKING_RATE)))
                .withMessage("\\class{rate} marks a symbol that the head does not declare");
    }

    @Test
    void rejectsSymbolsThatNoFormulaMarks() {
        var symbols = symbols(Map.of("rate", new Declaration("rate", null, List.of())));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> symbols.check(List.of(new Text("It decays at the rate $\\lambda$."))))
                .withMessage("No formula marks the symbol rate");
    }

    @Test
    void rejectsIdentifiersThatNoCodeOfTheLessonShows() {
        var symbols = symbols(Map.of("rate", new Declaration("rate", null, List.of("rate", "decayRate"))));
        symbols.occurrencesIn(METHOD);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> symbols.check(List.of(MARKING_RATE)))
                .withMessage("No code of the lesson shows decayRate, which holds the symbol rate");
    }

    private static Symbols symbols(Map<String, Declaration> declarations) {
        return new Symbols(declarations, PARAMETERS, READOUTS);
    }
}
