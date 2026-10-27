package dev.simlab.server.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.simlab.core.simulations.cloth.ClothDefinition;
import dev.simlab.core.simulations.galaxy.GalaxyDefinition;
import dev.simlab.core.simulations.lorenz.LorenzDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(SimulationController.class)
@Import({SimulationService.class, ClothDefinition.class, GalaxyDefinition.class, LorenzDefinition.class})
class SimulationControllerTests {

    private final MockMvcTester mvc;

    SimulationControllerTests(@Autowired MockMvcTester mvc) {
        this.mvc = mvc;
    }

    @Test
    void listsTheSimulationsWithTheKindsValuesAndEffectsOfTheirParameters() {
        assertThat(mvc.get().uri("/api/simulations")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                [
                  {
                    "type": "cloth",
                    "name": "Cloth",
                    "description": "%s",
                    "timeUnit": "s",
                    "parameters": [
                      {
                        "kind": "Range", "name": "wind", "label": "v", "unit": "m/s",
                        "min": 0.0, "max": 8.0, "defaultValue": 2.0, "effect": "LIVE"
                      },
                      {
                        "kind": "Range", "name": "gusts", "label": "Δv", "unit": "m/s",
                        "min": 0.0, "max": 4.0, "defaultValue": 1.0, "effect": "LIVE"
                      },
                      {
                        "kind": "Range", "name": "gravity", "label": "g", "unit": "m/s²",
                        "min": 0.0, "max": 20.0, "defaultValue": 9.81, "effect": "LIVE"
                      }
                    ]
                  },
                  {
                    "type": "galaxies",
                    "name": "Galaxy collision",
                    "description": "%s",
                    "timeUnit": "Myr",
                    "parameters": [
                      {
                        "kind": "Range", "name": "pace", "label": "Δt", "unit": "Myr/s",
                        "min": 1.0, "max": 40.0, "defaultValue": 15.0, "effect": "LIVE"
                      },
                      {
                        "kind": "Range", "name": "openingAngle", "label": "θ", "unit": "",
                        "min": 0.4, "max": 1.5, "defaultValue": 0.7, "effect": "LIVE"
                      },
                      {
                        "kind": "Range", "name": "ratio", "label": "q", "unit": "",
                        "min": 0.2, "max": 1.0, "defaultValue": 1.0, "effect": "RESTART"
                      },
                      {
                        "kind": "Range", "name": "pericenter", "label": "rₚ", "unit": "kpc",
                        "min": 4.0, "max": 30.0, "defaultValue": 10.0, "effect": "RESTART"
                      },
                      {
                        "kind": "Range", "name": "inclination", "label": "i", "unit": "°",
                        "min": 0.0, "max": 180.0, "defaultValue": 60.0, "effect": "RESTART"
                      }
                    ]
                  },
                  {
                    "type": "lorenz",
                    "name": "Lorenz attractor",
                    "description": "%s",
                    "timeUnit": "",
                    "parameters": [
                      {
                        "kind": "Range", "name": "sigma", "label": "σ", "unit": "",
                        "min": 0.1, "max": 30.0, "defaultValue": 10.0, "effect": "LIVE"
                      },
                      {
                        "kind": "Range", "name": "rho", "label": "ρ", "unit": "",
                        "min": 0.0, "max": 60.0, "defaultValue": 28.0, "effect": "LIVE"
                      },
                      {
                        "kind": "Range", "name": "beta", "label": "β", "unit": "",
                        "min": 0.1, "max": 10.0, "defaultValue": 2.6666666666666665, "effect": "LIVE"
                      },
                      {
                        "kind": "Choice", "name": "order", "label": "p",
                        "options": [{"value": 1.0, "label": "Euler"}, {"value": 4.0, "label": "Runge-Kutta 4"}],
                        "defaultValue": 4.0, "effect": "LIVE"
                      },
                      {
                        "kind": "Range", "name": "step", "label": "h", "unit": "",
                        "min": 0.001, "max": 0.2, "defaultValue": 0.005, "effect": "LIVE"
                      }
                    ]
                  }
                ]
                """.formatted(
                        new ClothDefinition().description(),
                        new GalaxyDefinition().description(),
                        new LorenzDefinition().description()));
    }
}
