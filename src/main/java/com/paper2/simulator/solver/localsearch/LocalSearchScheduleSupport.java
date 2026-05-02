package com.paper2.simulator.solver.localsearch;

import java.util.ArrayList;
import java.util.List;

import com.paper2.domain.Depot;
import com.paper2.domain.Graph;
import com.paper2.domain.Patient;
import com.paper2.domain.Porter;
import com.paper2.domain.RouteTiming;
import com.paper2.domain.Schedule;
import com.paper2.domain.Solution;
import com.paper2.domain.TimeObject;
import com.paper2.metrics.inventory.DepotSelectionByObjective;
import com.paper2.metrics.WheelchairDepotViolationSecondsCalculator;

/**
 * Materializes and edits {@code dummy → … → last real} chains on working {@link Schedule}s,
 * and recomputes times with the same rule as {@link Solution#removeIdle()}.
 */
public final class LocalSearchScheduleSupport {

    private LocalSearchScheduleSupport() {}

    /**
     * Chain order: index 0 = dummy, 1… = real patients through the last ({@code next == null}).
     */
    public static List<Patient> orderedNodes(Schedule schedule) {
        if (schedule == null) {
            return new ArrayList<>();
        }
        return schedule.orderedPatientsFromStart();
    }

    /** Updates {@code previous}/{@code next} pointers only, in list order. */
    public static void relinkSequential(List<Patient> list) {
        for (int k = 0; k < list.size(); k++) {
            Patient p = list.get(k);
            p.setPrevious(k > 0 ? list.get(k - 1) : null);
            p.setNext(k + 1 < list.size() ? list.get(k + 1) : null);
        }
    }

    /**
     * Links {@code nodes} in sequence and updates {@link Schedule#setStart}, end, and {@code amountOfPatients}.
     */
    public static void rewireSchedule(Schedule schedule, List<Patient> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        relinkSequential(nodes);
        schedule.setStart(nodes.get(0));
        schedule.setEnd(nodes.get(nodes.size() - 1));
        schedule.setAmountOfPatients(Math.max(0, nodes.size() - 1));
        schedule.setEndTime(schedule.getEnd().getEndTime());
    }

    /**
     * Deep copy of the chain to evaluate moves without mutating the solution.
     */
    public static List<Patient> copyChainDeep(List<Patient> nodes) {
        List<Patient> copies = new ArrayList<>(nodes.size());
        for (Patient p : nodes) {
            copies.add(new Patient(p));
        }
        relinkSequential(copies);
        return copies;
    }

    /**
     * Recomputes {@code start}/{@code end} along the chain from the dummy; when {@code solution} and
     * {@code scheduleId} are valid, travel uses depot detours per {@link Porter#shouldGoToDepot} and
     * {@link DepotSelectionByObjective#buildPlan(Solution, java.util.List)} with this chain as override.
     */
    public static void recomputeTimesFromDummy(
            Patient dummyHead, Graph graph, TimeObject simulatorClock, Solution solution, int scheduleId) {
        Patient first = dummyHead;
        if (first == null) {
            return;
        }
        DepotSelectionByObjective.DepotLegPlan plan = null;
        List<Depot> depots = null;
        if (solution != null
                && scheduleId >= 0
                && solution.getSchedules() != null
                && scheduleId < solution.getSchedules().size()
                && solution.getDepots() != null
                && !solution.getDepots().isEmpty()) {
            depots = solution.getDepots();
            List<Patient> trialChainForPlan = new ArrayList<>();
            for (Patient x = dummyHead; x != null; x = x.getNext()) {
                trialChainForPlan.add(x);
            }
            Patient curDirect = first.getNext();
            while (curDirect != null) {
                Patient prevD = curDirect.getPrevious();
                TimeObject erD =
                        RouteTiming.earliestStart(
                                curDirect.getRequestedTime(), prevD.getEndTime(), simulatorClock);
                TimeObject ttD =
                        graph.getTravelTimeBetweenTwoLocations(prevD.getLocation(), curDirect.getLocation());
                curDirect.updateTimeFromLastPatient(erD, ttD);
                curDirect = curDirect.getNext();
            }
            List<List<Patient>> overrides =
                    DepotSelectionByObjective.emptyChainOverrides(solution.getSchedules().size());
            overrides.set(scheduleId, trialChainForPlan);
            plan = DepotSelectionByObjective.buildPlan(solution, overrides);
        }
        Patient current = first.getNext();
        int legIndex = 0;
        while (current != null) {
            Patient previous = current.getPrevious();
            TimeObject earliest =
                    RouteTiming.earliestStart(
                            current.getRequestedTime(), previous.getEndTime(), simulatorClock);
            legIndex++;
            Porter porter = solution.getSchedules().get(scheduleId).getPorter();
            Depot depotForLeg = null;
            if (plan != null && depots != null && porter != null) {
                depotForLeg =
                        DepotSelectionByObjective.depotForLeg(
                                plan, scheduleId, legIndex, previous, depots, graph);
            }
            TimeObject travelTime =
                    RouteTiming.travelBetweenPatients(graph, previous, current, porter, depotForLeg);
            current.updateTimeFromLastPatient(earliest, travelTime);
            current = current.getNext();
        }
    }

    /**
     * Sum of unweighted tardiness on non-dummy nodes (requires up-to-date times).
     */
    public static long tardinessSumOnNodes(List<Patient> nodes) {
        return TotalUnweightedTardinessObjective.sumTardinessSeconds(nodes);
    }

    public static long tardinessSumOnSchedule(Schedule schedule) {
        return tardinessSumOnNodes(orderedNodes(schedule));
    }

    /**
     * Full objective: unchanged schedules use current state; schedule {@code modifiedId} uses
     * {@code trialChain} (times already recomputed from the dummy).
     */
    public static long objectiveWithTrialChain(Solution solution, int modifiedScheduleId, List<Patient> trialChain) {
        long sum = 0;
        List<Schedule> schedules = solution.getSchedules();
        for (int sid = 0; sid < schedules.size(); sid++) {
            if (sid == modifiedScheduleId) {
                sum += tardinessSumOnNodes(trialChain);
            } else {
                sum += tardinessSumOnSchedule(schedules.get(sid));
            }
        }
        return sum;
    }

    /**
     * Two schedules replaced by evaluation chains (times already recomputed in each).
     */
    public static long objectiveWithTwoTrialChains(
            Solution solution, int id1, List<Patient> chain1, int id2, List<Patient> chain2) {
        long sum = 0;
        List<Schedule> schedules = solution.getSchedules();
        for (int sid = 0; sid < schedules.size(); sid++) {
            if (sid == id1) {
                sum += tardinessSumOnNodes(chain1);
            } else if (sid == id2) {
                sum += tardinessSumOnNodes(chain2);
            } else {
                sum += tardinessSumOnSchedule(schedules.get(sid));
            }
        }
        return sum;
    }

    public static long evaluateTrialChain(Solution solution, int scheduleId, List<Patient> trialCopy) {
        if (trialCopy.isEmpty()) {
            return objectiveWithTrialChain(solution, scheduleId, trialCopy);
        }
        Patient dummy = trialCopy.get(0);
        recomputeTimesFromDummy(
                dummy, solution.getGraph(), solution.getSimulatorClock(), solution, scheduleId);
        return objectiveWithTrialChain(solution, scheduleId, trialCopy);
    }

    public static long evaluateTwoTrialChains(
            Solution solution, int id1, List<Patient> c1, int id2, List<Patient> c2) {
        if (!c1.isEmpty()) {
            recomputeTimesFromDummy(
                    c1.get(0), solution.getGraph(), solution.getSimulatorClock(), solution, id1);
        }
        if (!c2.isEmpty()) {
            recomputeTimesFromDummy(
                    c2.get(0), solution.getGraph(), solution.getSimulatorClock(), solution, id2);
        }
        return objectiveWithTwoTrialChains(solution, id1, c1, id2, c2);
    }

    /**
     * {@link #evaluateTrialChain(Solution, int, List)} plus depot wheelchair violation penalty (trial chain
     * included in violation ledger).
     */
    public static long evaluateCombinedTrialChain(Solution solution, int modifiedScheduleId, List<Patient> trialCopy) {
        if (trialCopy.isEmpty()) {
            return evaluateTrialChain(solution, modifiedScheduleId, trialCopy)
                    + depotPenaltyTermForOverrides(solution, modifiedScheduleId, trialCopy);
        }
        Patient dummyHead = trialCopy.get(0);
        recomputeTimesFromDummy(
                dummyHead, solution.getGraph(), solution.getSimulatorClock(), solution, modifiedScheduleId);
        long tardinessPart = objectiveWithTrialChain(solution, modifiedScheduleId, trialCopy);
        return tardinessPart + depotPenaltyTermForOverrides(solution, modifiedScheduleId, trialCopy);
    }

    /**
     * {@link #evaluateTwoTrialChains(Solution, int, List, int, List)} plus depot violation penalty for both trial chains.
     */
    public static long evaluateCombinedTwoTrialChains(
            Solution solution, int id1, List<Patient> c1, int id2, List<Patient> c2) {
        if (!c1.isEmpty()) {
            recomputeTimesFromDummy(
                    c1.get(0), solution.getGraph(), solution.getSimulatorClock(), solution, id1);
        }
        if (!c2.isEmpty()) {
            recomputeTimesFromDummy(
                    c2.get(0), solution.getGraph(), solution.getSimulatorClock(), solution, id2);
        }
        long tardinessPart = objectiveWithTwoTrialChains(solution, id1, c1, id2, c2);
        List<List<Patient>> overrides =
                DepotSelectionByObjective.emptyChainOverrides(solution.getSchedules().size());
        overrides.set(id1, c1);
        overrides.set(id2, c2);
        long violationSeconds =
                WheelchairDepotViolationSecondsCalculator.totalViolationSecondsAcrossDepots(solution, overrides);
        return tardinessPart + violationSeconds * (long) solution.getDepotInventoryViolationPenaltyCoefficient();
    }

    private static long depotPenaltyTermForOverrides(
            Solution solution, int modifiedScheduleId, List<Patient> trialChain) {
        List<List<Patient>> overrides =
                DepotSelectionByObjective.emptyChainOverrides(solution.getSchedules().size());
        overrides.set(modifiedScheduleId, trialChain);
        long violationSeconds =
                WheelchairDepotViolationSecondsCalculator.totalViolationSecondsAcrossDepots(solution, overrides);
        return violationSeconds * (long) solution.getDepotInventoryViolationPenaltyCoefficient();
    }

}
