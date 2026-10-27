package dev.simlab.server.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import dev.simlab.server.lesson.MethodSource.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Cuts methods out of the sources among the test resources, in {@code sources/dev/simlab/server/lesson} */
class SourceCodeTest {

    private final SourceCode sources = new SourceCode();

    @Test
    void cutsAMethodOutOfItsFileFromItsJavadocToItsClosingBrace() {
        MethodSource method = sources.method(Reference.parse("Methods#documented"));

        assertThat(method.packageName()).isEqualTo("dev.simlab.server.lesson");
        assertThat(method.path()).isEqualTo("dev/simlab/server/lesson/Methods.java");
        assertThat(method.firstLine()).isEqualTo(8);
        assertThat(method.lastLine()).isEqualTo(12);
        assertThat(method.code().lines())
                .containsExactly(
                        "    /** has Javadoc and an annotation */",
                        "    @Deprecated",
                        "    void documented() {",
                        "        // a comment inside the method",
                        "    }");
    }

    @Test
    void startsAtTheCommentAboveAMethod() {
        MethodSource method = sources.method(Reference.parse("Methods#commented"));

        assertThat(method.firstLine()).isEqualTo(14);
        assertThat(method.code()).isEqualTo("    // has a line comment\n    void commented() {}");
    }

    @Test
    void startsAtAMethodWithoutComment() {
        MethodSource method = sources.method(Reference.parse("Methods#bare"));

        assertThat(method.firstLine()).isEqualTo(17);
        assertThat(method.lastLine()).isEqualTo(17);
        assertThat(method.code()).isEqualTo("    void bare() {}");
    }

    @Test
    void findsTheIdentifiersOfAMethodInTheOrderOfTheCodeButNotInItsCommentsAndStrings() {
        MethodSource method = sources.method(Reference.parse("Methods#decayed"));

        assertThat(method.identifiers())
                .extracting(Identifier::name)
                .containsExactly(
                        "decayed", "amount", "rate", "dt", "String", "unit", "amount", "Math", "exp", "rate", "dt");
        assertThat(method.identifiers())
                .filteredOn(identifier -> identifier.name().equals("rate"))
                .containsExactly(new Identifier("rate", 24, 42), new Identifier("rate", 26, 35));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = ';',
            value = {"Methods#scaled(double[], double); 19", "Methods#scaled(List, int...); 21"})
    void tellsMethodsOfTheSameNameApartByTheTypesOfTheirParameters(String reference, int line) {
        assertThat(sources.method(Reference.parse(reference)).firstLine()).isEqualTo(line);
    }

    @Test
    void rejectsAReferenceThatFitsMethodsOfTheSameName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> sources.method(Reference.parse("Methods#scaled")))
                .withMessage("Methods#scaled names 2 methods, which the types of their parameters tell apart");
    }

    @Test
    void rejectsAReferenceWithTypesOfParametersThatNoMethodHas() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> sources.method(Reference.parse("Methods#scaled(double)")))
                .withMessage("sim-core has no method Methods#scaled(double)");
    }

    @Test
    void rejectsUnknownMethods() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> sources.method(Reference.parse("Methods#unknown")))
                .withMessage("sim-core has no method Methods#unknown");
    }

    @Test
    void rejectsUnknownClasses() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> sources.method(Reference.parse("Unknown#method")))
                .withMessage("sim-core has no class Unknown");
    }

    @Test
    void rejectsNamesOfSeveralClasses() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> sources.method(Reference.parse("Twin#method")))
                .withMessageStartingWith("sim-core has 2 classes named Twin: ");
    }

    @Test
    void reportsSourcesThatCannotBeParsed() {
        assertThatIllegalStateException()
                .isThrownBy(() -> sources.method(Reference.parse("Unparsable#method")))
                .withMessageContaining("Unparsable.java")
                .withMessageContaining("cannot be parsed");
    }

    @Test
    void readsTheSourcesOfSimCoreFromItsJar() {
        MethodSource method = sources.method(Reference.parse("RungeKutta4#step"));

        assertThat(method.packageName()).isEqualTo("dev.simlab.core.toolkit.ode");
        assertThat(method.path()).isEqualTo("dev/simlab/core/toolkit/ode/RungeKutta4.java");
        assertThat(method.code())
                .startsWith("    /**")
                .contains("public void step(")
                .endsWith("    }");
    }
}
