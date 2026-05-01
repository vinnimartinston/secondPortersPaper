package com.paper2.simulator.solver.localsearch;

import com.paper2.domain.Solution;

/**
 * Local search neighborhood: improves until a local optimum (full scan + best move per iteration).
 */
public abstract class AbstractLocalSearchNeighborhood {

    public abstract void improveUntilLocalOptimum(Solution solution);
}
