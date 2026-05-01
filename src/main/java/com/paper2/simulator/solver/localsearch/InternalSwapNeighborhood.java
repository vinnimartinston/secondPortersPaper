package com.paper2.simulator.solver.localsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.paper2.domain.Patient;
import com.paper2.domain.Schedule;
import com.paper2.domain.Solution;

/**
 * Swaps two jobs on the same machine (C++ Internal Swap neighborhood).
 */
public final class InternalSwapNeighborhood extends AbstractLocalSearchNeighborhood {

    private record InternalSwapMove(int scheduleId, int i, int j) {}

    @Override
    public void improveUntilLocalOptimum(Solution solution) {
        List<Schedule> schedules = solution.getSchedules();
        if (schedules == null) {
            return;
        }
        while (true) {
            InternalSwapMove best = null;
            long bestObj = TotalUnweightedTardinessObjective.evaluateCombined(solution);

            for (int m = 0; m < schedules.size(); m++) {
                Schedule schedule = schedules.get(m);
                List<Patient> nodes = LocalSearchScheduleSupport.orderedNodes(schedule);
                int size = nodes.size();
                if (size <= 2) {
                    continue;
                }
                for (int i = 1; i <= size - 2; i++) {
                    for (int j = i + 1; j <= size - 1; j++) {
                        List<Patient> trial = LocalSearchScheduleSupport.copyChainDeep(nodes);
                        Collections.swap(trial, i, j);
                        LocalSearchScheduleSupport.relinkSequential(trial);
                        long obj = LocalSearchScheduleSupport.evaluateCombinedTrialChain(solution, m, trial);
                        if (obj < bestObj) {
                            bestObj = obj;
                            best = new InternalSwapMove(m, i, j);
                        }
                    }
                }
            }

            if (best == null) {
                break;
            }
            apply(schedules.get(best.scheduleId), best);
            solution.removeIdle();
            solution.rebuildWorkingInventory();
        }
    }

    private static void apply(Schedule schedule, InternalSwapMove move) {
        List<Patient> nodes = new ArrayList<>(LocalSearchScheduleSupport.orderedNodes(schedule));
        Collections.swap(nodes, move.i, move.j);
        LocalSearchScheduleSupport.relinkSequential(nodes);
        LocalSearchScheduleSupport.rewireSchedule(schedule, nodes);
    }
}
