package com.paper2.simulator;

import com.paper2.domain.Solution;

/**
 * Core simulation orchestration.
 * <p>
 * Suggested location: {@code com.paper2.simulator} — domain of the simulator, alongside
 * {@code regras}, {@code solver}, etc.
 */
public interface Solver {

    Solution solve(Solution solution);
}