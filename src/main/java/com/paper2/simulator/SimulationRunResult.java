package com.paper2.simulator;

import com.paper2.domain.Solution;
import com.paper2.dto.SimulationOutputDto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Result of {@link Simulator#start(com.paper2.dto.InputDto)}: DTO for metadata + domain solution. */
@Getter
@AllArgsConstructor
public class SimulationRunResult {
    private final SimulationOutputDto output;
    private final Solution solution;
}
