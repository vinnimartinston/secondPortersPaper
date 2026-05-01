package com.paper2.simulator;

import com.paper2.domain.Input;
import com.paper2.domain.Solution;
import com.paper2.dto.InputDto;
import com.paper2.dto.SimulationOutputDto;
import com.paper2.mapper.InputMapper;
import com.paper2.simulator.policies.PolicyOne;

/**
 * Core simulation orchestration.
 * <p>
 * Suggested location: {@code com.paper2.simulador} — domain of the simulator, alongside
 * {@code regras}, {@code solver}, etc.
 */
public class Simulator {

    /**
     * Entry point for a single instance: wire the main pipeline here as you add steps
     * (rules, solver, state updates, …).
     *
     * @param input fully loaded instance
     * @return {@link SimulationRunResult} with run metadata ({@link SimulationOutputDto}) and {@link Solution}
     */
    public SimulationRunResult start(InputDto inputDto) {
        if (inputDto == null || inputDto.getName() == null) {
            return new SimulationRunResult(
                    SimulationOutputDto.error("unknown", "Input or name is null"), null);
        }

        Input input = InputMapper.toDomain(inputDto);

        SimulationPolicy policy = new PolicyOne();
        Solution solution = policy.apply(input);

        return new SimulationRunResult(
                SimulationOutputDto.ok(
                        input.getName(), "Placeholder — pipeline not implemented yet"),
                solution);
    }
}
