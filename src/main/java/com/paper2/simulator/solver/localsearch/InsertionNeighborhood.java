package com.paper2.simulator.solver.localsearch;

import java.util.ArrayList;
import java.util.List;

import com.paper2.domain.Patient;
import com.paper2.domain.Schedule;
import com.paper2.domain.Solution;

/**
 * Removes a job from one machine and inserts it on another (C++ Insertion neighborhood).
 */
public final class InsertionNeighborhood extends AbstractLocalSearchNeighborhood {

    private record InsertionMove(int m1, int i, int m2, int j) {}

    @Override
    public void improveUntilLocalOptimum(Solution solution) {
        List<Schedule> schedules = solution.getSchedules();
        if (schedules == null) {
            return;
        }
        while (true) {
            InsertionMove best = null;
            long bestObj = TotalUnweightedTardinessObjective.evaluateCombined(solution);

            for (int m1 = 0; m1 < schedules.size(); m1++) {
                List<Patient> nodes1 = LocalSearchScheduleSupport.orderedNodes(schedules.get(m1));
                if (nodes1.size() <= 1) {
                    continue;
                }
                for (int i = 1; i <= nodes1.size() - 1; i++) {
                    for (int m2 = 0; m2 < schedules.size(); m2++) {
                        if (m1 == m2) {
                            continue;
                        }
                        List<Patient> nodes2 = LocalSearchScheduleSupport.orderedNodes(schedules.get(m2));
                        for (int j = 1; j <= nodes2.size(); j++) {
                            List<Patient> t1 = LocalSearchScheduleSupport.copyChainDeep(nodes1);
                            List<Patient> t2 = LocalSearchScheduleSupport.copyChainDeep(nodes2);
                            Patient job = t1.remove(i);
                            LocalSearchScheduleSupport.relinkSequential(t1);
                            t2.add(j, job);
                            LocalSearchScheduleSupport.relinkSequential(t2);
                            long obj =
                                    LocalSearchScheduleSupport.evaluateCombinedTwoTrialChains(
                                            solution, m1, t1, m2, t2);
                            if (obj < bestObj) {
                                bestObj = obj;
                                best = new InsertionMove(m1, i, m2, j);
                            }
                        }
                    }
                }
            }

            if (best == null) {
                break;
            }
            apply(schedules.get(best.m1), schedules.get(best.m2), best);
            solution.removeIdle();
            solution.rebuildWorkingInventory();
        }
    }

    private static void apply(Schedule s1, Schedule s2, InsertionMove move) {
        List<Patient> t1 = new ArrayList<>(LocalSearchScheduleSupport.orderedNodes(s1));
        List<Patient> t2 = new ArrayList<>(LocalSearchScheduleSupport.orderedNodes(s2));
        Patient job = t1.remove(move.i);
        LocalSearchScheduleSupport.relinkSequential(t1);
        t2.add(move.j, job);
        LocalSearchScheduleSupport.relinkSequential(t2);
        LocalSearchScheduleSupport.rewireSchedule(s1, t1);
        LocalSearchScheduleSupport.rewireSchedule(s2, t2);
    }
}
