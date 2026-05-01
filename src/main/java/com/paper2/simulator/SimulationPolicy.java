package com.paper2.simulator;

import com.paper2.domain.Input;
import com.paper2.domain.Solution;

/**
 * Core simulation orchestration.
 * <p>
 * Suggested location: {@code com.paper2.simulator} — domain of the simulator, alongside
 * {@code regras}, {@code solver}, etc.
 */
public interface SimulationPolicy {

    Solution apply(Input input);

    /**
     * Consolidates {@code workingSolution} (scheduler output) into {@code finalSolution} per policy
     * rules. Patient definitions and graph come from the solutions themselves
     * (e.g. {@link Solution#getPatients()}, {@link Solution#getGraph()}).
     */
    void appendFinalResults(Solution workingSolution);

    /**
     * Whether the online loop (e.g. {@code solve} → update clock → {@link #appendFinalResults}) should
     * continue. Each policy defines the criterion (job cursor, empty queue, time horizon, etc.); the
     * state for that criterion lives in the implementation.
     *
     * @param input problem instance
     * @param solution state before the next solver call in this round
     * @return {@code true} to run another loop iteration
     */
    boolean shouldContinueOnlineLoop(Input input, Solution solution);
}