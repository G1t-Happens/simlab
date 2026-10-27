package dev.simlab.server.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import dev.simlab.server.lesson.Lesson.Calculation;
import dev.simlab.server.lesson.Lesson.Code;
import dev.simlab.server.lesson.Lesson.Experiment;
import dev.simlab.server.lesson.Lesson.Occurrence;
import dev.simlab.server.lesson.Lesson.Origin;
import dev.simlab.server.lesson.Lesson.Symbol;
import dev.simlab.server.lesson.Lesson.Text;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(LessonController.class)
@MockitoBean(types = LessonService.class)
class LessonControllerTests {

    private final MockMvcTester mvc;
    private final LessonService lessons;

    LessonControllerTests(@Autowired MockMvcTester mvc, @Autowired LessonService lessons) {
        this.mvc = mvc;
        this.lessons = lessons;
    }

    @Test
    void returnsTheLessonOfASimulationWithItsSymbolsAndTheKindOfEveryBlock() {
        var derivative = new Code(
                "LorenzSystem#derivative",
                "dev/simlab/core/simulations/lorenz/LorenzSystem.java",
                23,
                24,
                "    @Override\n    public void derivative(double t, double[] y, double[] dydt) {…}",
                Origin.SIMULATION,
                URI.create("https://github.com/G1t-Happens/simlab/blob/f00d/LorenzSystem.java#L23-L24"),
                List.of(new Occurrence("y", 24, 47, 1)));
        var system = new Code(
                "OdeSystem#derivative",
                "dev/simlab/core/toolkit/ode/OdeSystem.java",
                8,
                8,
                "    void derivative(double t, double[] y, double[] dydt);",
                Origin.TOOLKIT,
                null,
                List.of());
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("rho", 0.5);
        values.put("sigma", 10.0);
        var conduction =
                new Experiment("What do the particles do for $\\rho < 1$?", values, "They fall into the origin.");
        var rate = new Calculation("\\class{sigma}{\\sigma} \\cdot (\\class{y}{y} - x)");
        given(lessons.find("lorenz"))
                .willReturn(Optional.of(new Lesson(
                        List.of(new Symbol("sigma", "sigma", null), new Symbol("y", null, "y")),
                        List.of(
                                new Text("Three equations: $\\dot x = \\class{sigma}{\\sigma} (y - x)$, …"),
                                derivative,
                                system,
                                conduction,
                                rate))));

        assertThat(mvc.get().uri("/api/simulations/lorenz/lesson"))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        {
                          "symbols": [
                            { "name": "sigma", "parameter": "sigma", "readout": null },
                            { "name": "y", "parameter": null, "readout": "y" }
                          ],
                          "blocks": [
                            {
                              "kind": "Text",
                              "markdown": "Three equations: $\\\\dot x = \\\\class{sigma}{\\\\sigma} (y - x)$, …"
                            },
                            {
                              "kind": "Code",
                              "reference": "LorenzSystem#derivative",
                              "path": "dev/simlab/core/simulations/lorenz/LorenzSystem.java",
                              "firstLine": 23,
                              "lastLine": 24,
                              "code": "    @Override\\n    public void derivative(double t, double[] y, double[] dydt) {…}",
                              "origin": "SIMULATION",
                              "url": "https://github.com/G1t-Happens/simlab/blob/f00d/LorenzSystem.java#L23-L24",
                              "occurrences": [{ "symbol": "y", "line": 24, "column": 47, "length": 1 }]
                            },
                            {
                              "kind": "Code",
                              "reference": "OdeSystem#derivative",
                              "path": "dev/simlab/core/toolkit/ode/OdeSystem.java",
                              "firstLine": 8,
                              "lastLine": 8,
                              "code": "    void derivative(double t, double[] y, double[] dydt);",
                              "origin": "TOOLKIT",
                              "url": null,
                              "occurrences": []
                            },
                            {
                              "kind": "Experiment",
                              "question": "What do the particles do for $\\\\rho < 1$?",
                              "values": { "rho": 0.5, "sigma": 10.0 },
                              "explanation": "They fall into the origin."
                            },
                            {
                              "kind": "Calculation",
                              "formula": "\\\\class{sigma}{\\\\sigma} \\\\cdot (\\\\class{y}{y} - x)"
                            }
                          ]
                        }
                        """);
    }

    @Test
    void answersNotFoundForUnknownSimulations() {
        given(lessons.find("quantum")).willReturn(Optional.empty());

        assertThat(mvc.get().uri("/api/simulations/quantum/lesson"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("There is no simulation of type 'quantum'");
    }
}
