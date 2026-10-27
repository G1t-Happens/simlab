package dev.simlab.server.simulation;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulations")
class SimulationController {

    private final SimulationService simulations;

    SimulationController(SimulationService simulations) {
        this.simulations = simulations;
    }

    /** Lists the simulations with their parameter ranges, which the browser builds its controls from */
    @GetMapping
    List<SimulationResponse> list() {
        return simulations.all().stream().map(SimulationResponse::from).toList();
    }
}
