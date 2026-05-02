package com.paper2.simulator.solver;

import com.paper2.domain.Solution;
import com.paper2.simulator.Solver;

/**
 * Core simulation orchestration.
 * <p>
 * Suggested location: {@code com.paper2.simulator} — domain of the simulator, alongside
 * {@code regras}, {@code solver}, etc.
 */
public class LocalSearchSolver implements Solver {

    @Override
    public Solution solve(Solution initialSolution) {
        Solution solution = new SequentialInsertionSolver().solve(initialSolution);
        // new LocalSearchCoordinator().run(solution);
        solution.removeIdle();
        return solution;
    }
}