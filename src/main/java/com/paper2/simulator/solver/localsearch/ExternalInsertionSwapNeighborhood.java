package com.paper2.simulator.solver.localsearch;

import java.util.ArrayList;
import java.util.List;

import com.paper2.domain.Patient;
import com.paper2.domain.Schedule;
import com.paper2.domain.Solution;

/**
 * External swap with reinsertion: two jobs on distinct machines (C++ External + Insertion neighborhood).
 */
public final class ExternalInsertionSwapNeighborhood extends AbstractLocalSearchNeighborhood {

    /** p1 = insertion position of job1 on m2; p2 = insertion position of job2 on m1 (indices as in C++). */
    private record ExternalMove(int m1, int j1, int p1, int m2, int j2, int p2) {}

    @Override
    public void improveUntilLocalOptimum(Solution solution) {
        List<Schedule> schedules = solution.getSchedules();
        if (schedules == null) {
            return;
        }
        while (true) {
            ExternalMove best = null;
            long bestObj = TotalUnweightedTardinessObjective.evaluateCombined(solution);

            for (int m1 = 0; m1 < schedules.size() - 1; m1++) {
                List<Patient> nodes1 = LocalSearchScheduleSupport.orderedNodes(schedules.get(m1));
                int size1 = nodes1.size();
                if (size1 <= 1) {
                    continue;
                }
                for (int m2 = m1 + 1; m2 < schedules.size(); m2++) {
                    List<Patient> nodes2 = LocalSearchScheduleSupport.orderedNodes(schedules.get(m2));
                    int size2 = nodes2.size();
                    if (size2 <= 1) {
                        continue;
                    }
                    for (int j1 = 1; j1 <= size1 - 1; j1++) {
                        for (int j2 = 1; j2 <= size2 - 1; j2++) {
                            for (int p1 = 1; p1 <= size2 - 1; p1++) {
                                for (int p2 = 1; p2 <= size1 - 1; p2++) {
                                    List<Patient> t1 = LocalSearchScheduleSupport.copyChainDeep(nodes1);
                                    List<Patient> t2 = LocalSearchScheduleSupport.copyChainDeep(nodes2);
                                    Patient job1 = t1.remove(j1);
                                    LocalSearchScheduleSupport.relinkSequential(t1);
                                    Patient job2 = t2.remove(j2);
                                    LocalSearchScheduleSupport.relinkSequential(t2);
                                    t1.add(p2, job2);
                                    LocalSearchScheduleSupport.relinkSequential(t1);
                                    t2.add(p1, job1);
                                    LocalSearchScheduleSupport.relinkSequential(t2);
                                    long obj =
                                            LocalSearchScheduleSupport.evaluateCombinedTwoTrialChains(
                                                    solution, m1, t1, m2, t2);
                                    if (obj < bestObj) {
                                        bestObj = obj;
                                        best = new ExternalMove(m1, j1, p1, m2, j2, p2);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (best == null) {
                break;
            }
            apply(
                    schedules.get(best.m1),
                    schedules.get(best.m2),
                    best);
            solution.removeIdle();
            solution.rebuildWorkingInventory();
        }
    }

    private static void apply(Schedule s1, Schedule s2, ExternalMove move) {
        List<Patient> t1 = new ArrayList<>(LocalSearchScheduleSupport.orderedNodes(s1));
        List<Patient> t2 = new ArrayList<>(LocalSearchScheduleSupport.orderedNodes(s2));
        Patient job1 = t1.remove(move.j1);
        LocalSearchScheduleSupport.relinkSequential(t1);
        Patient job2 = t2.remove(move.j2);
        LocalSearchScheduleSupport.relinkSequential(t2);
        t1.add(move.p2, job2);
        LocalSearchScheduleSupport.relinkSequential(t1);
        t2.add(move.p1, job1);
        LocalSearchScheduleSupport.relinkSequential(t2);
        LocalSearchScheduleSupport.rewireSchedule(s1, t1);
        LocalSearchScheduleSupport.rewireSchedule(s2, t2);
    }
}
