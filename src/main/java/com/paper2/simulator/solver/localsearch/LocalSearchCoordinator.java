package com.paper2.simulator.solver.localsearch;

import com.paper2.domain.Solution;

/**
 * Orchestrates local search in order Internal Swap → Insertion → External Insertion Swap,
 * repeating while any phase improves the best objective (mirror of {@code Local_Search} in C++).
 */
public final class LocalSearchCoordinator {

    private final AbstractLocalSearchNeighborhood internalSwap = new InternalSwapNeighborhood();
    private final AbstractLocalSearchNeighborhood insertion = new InsertionNeighborhood();
    private final AbstractLocalSearchNeighborhood externalInsertionSwap = new ExternalInsertionSwapNeighborhood();

    public void run(Solution solution) {
        if (solution == null || solution.getSchedules() == null) {
            return;
        }
        solution.removeIdle();
        solution.rebuildWorkingInventory();
        long bestOf = TotalUnweightedTardinessObjective.evaluateCombined(solution);
        while (true) {
            boolean improvedThisRound = false;

            internalSwap.improveUntilLocalOptimum(solution);
            long v = TotalUnweightedTardinessObjective.evaluateCombined(solution);
            if (v < bestOf) {
                bestOf = v;
                improvedThisRound = true;
            }

            insertion.improveUntilLocalOptimum(solution);
            v = TotalUnweightedTardinessObjective.evaluateCombined(solution);
            if (v < bestOf) {
                bestOf = v;
                improvedThisRound = true;
            }

            externalInsertionSwap.improveUntilLocalOptimum(solution);
            v = TotalUnweightedTardinessObjective.evaluateCombined(solution);
            if (v < bestOf) {
                bestOf = v;
                improvedThisRound = true;
            }

            if (!improvedThisRound) {
                break;
            }
        }
        TotalUnweightedTardinessObjective.syncSolutionField(solution);
    }
}
