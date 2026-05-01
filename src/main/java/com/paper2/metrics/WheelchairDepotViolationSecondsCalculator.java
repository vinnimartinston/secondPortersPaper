package com.paper2.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.paper2.domain.Depot;
import com.paper2.domain.DomainConstants;
import com.paper2.domain.Graph;
import com.paper2.domain.Patient;
import com.paper2.domain.Schedule;
import com.paper2.domain.Solution;
import com.paper2.domain.inventory.SolutionInventoryState;
import com.paper2.metrics.inventory.WheelchairDepotEdgeRules;
import com.paper2.metrics.inventory.WheelchairDepotEdgeRules.WheelchairDepotBalanceChange;

/**
 * Soft inventory model: measures how many seconds in the evaluation horizon have strictly negative wheelchair
 * stock at each depot. Uses committed final-schedule timeline plus working-route events from the current
 * schedules (or trial overrides). The horizon is walked in one-second steps.
 */
public final class WheelchairDepotViolationSecondsCalculator {

    private WheelchairDepotViolationSecondsCalculator() {}

    private record ViolationSweepOutcome(long totalSeconds, Map<Integer, Long> secondsByDepotId) {
        static ViolationSweepOutcome empty() {
            return new ViolationSweepOutcome(0L, Map.of());
        }
    }

    public static long totalViolationSecondsAcrossDepots(Solution solution) {
        return totalViolationSecondsAcrossDepots(solution, null);
    }

    public static Map<Integer, Long> violationSecondsPerDepot(Solution solution) {
        return new LinkedHashMap<>(computeViolationSweep(solution, null).secondsByDepotId());
    }

    public static long totalViolationSecondsAcrossDepots(
            Solution solution, List<List<Patient>> chainOverrideByScheduleIndex) {
        return computeViolationSweep(solution, chainOverrideByScheduleIndex).totalSeconds();
    }

    private static ViolationSweepOutcome computeViolationSweep(
            Solution solution, List<List<Patient>> chainOverrideByScheduleIndex) {
        if (solution == null
                || solution.getSchedules() == null
                || solution.getDepots() == null
                || solution.getDepots().isEmpty()) {
            return ViolationSweepOutcome.empty();
        }
        List<Depot> depots = solution.getDepots();
        List<Schedule> schedules = solution.getSchedules();
        if (chainOverrideByScheduleIndex != null && chainOverrideByScheduleIndex.size() != schedules.size()) {
            throw new IllegalArgumentException("chainOverrideByScheduleIndex size must match schedules size");
        }
        int windowStart = evaluationWindowStartSeconds(solution);
        int rawEnd = evaluationWindowEndExclusiveSeconds(solution, chainOverrideByScheduleIndex);
        int windowEndExclusive = capWindowEnd(windowStart, rawEnd);
        Graph graph = solution.getGraph();
        if (graph == null || windowEndExclusive <= windowStart) {
            return zerosPerDepot(depots);
        }

        Map<Integer, List<WheelchairDepotBalanceChange>> allByDepot =
                collectAllWorkingChangesByDepot(solution, chainOverrideByScheduleIndex);

        Map<Integer, Long> byDepotId = new LinkedHashMap<>();
        long totalViolations = 0;
        SolutionInventoryState state = solution.getInventoryState();
        for (Depot depot : depots) {
            int depotId = depot.getId();
            long initial =
                    computeInitialBalanceBeforeWindowStart(solution, state, depotId, windowStart, allByDepot);
            List<WheelchairDepotBalanceChange> inWindow =
                    filterChangesInWindow(allByDepot.getOrDefault(depotId, List.of()), windowStart, windowEndExclusive);
            long depotViolations =
                    violationSecondsForDepot(initial, inWindow, windowStart, windowEndExclusive);
            byDepotId.put(depotId, depotViolations);
            totalViolations += depotViolations;
        }
        return new ViolationSweepOutcome(totalViolations, Collections.unmodifiableMap(byDepotId));
    }

    private static int capWindowEnd(int windowStart, int rawEndExclusive) {
        int cap = windowStart + DomainConstants.MAX_EVALUATION_HORIZON_DURATION_SECONDS;
        return Math.min(rawEndExclusive, cap);
    }

    private static long computeInitialBalanceBeforeWindowStart(
            Solution solution,
            SolutionInventoryState state,
            int depotId,
            int windowStart,
            Map<Integer, List<WheelchairDepotBalanceChange>> allByDepot) {
        long balance;
        if (state != null) {
            balance = state.finalCommittedBalanceAt(depotId, windowStart - 1);
            int anchor = state.getIterationAnchorSeconds();
            for (WheelchairDepotBalanceChange c : allByDepot.getOrDefault(depotId, List.of())) {
                int t = c.timeSeconds();
                if (t >= anchor && t < windowStart) {
                    balance += c.wheelchairDelta();
                }
            }
        } else {
            balance =
                    solution.getDepots().stream()
                            .filter(d -> d.getId() == depotId)
                            .findFirst()
                            .map(Depot::getInitialWheelchairInventory)
                            .orElse(0);
            for (WheelchairDepotBalanceChange c : allByDepot.getOrDefault(depotId, List.of())) {
                if (c.timeSeconds() < windowStart) {
                    balance += c.wheelchairDelta();
                }
            }
        }
        return balance;
    }

    private static List<WheelchairDepotBalanceChange> filterChangesInWindow(
            List<WheelchairDepotBalanceChange> changes, int windowStart, int windowEndExclusive) {
        List<WheelchairDepotBalanceChange> out = new ArrayList<>();
        for (WheelchairDepotBalanceChange c : changes) {
            int t = c.timeSeconds();
            if (t >= windowStart && t < windowEndExclusive) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * Merges wheelchair depot events from all working schedules (or trial overrides).
     */
    private static Map<Integer, List<WheelchairDepotBalanceChange>> collectAllWorkingChangesByDepot(
            Solution solution, List<List<Patient>> chainOverrideByScheduleIndex) {
        Map<Integer, List<WheelchairDepotBalanceChange>> merged = new HashMap<>();
        List<Schedule> schedules = solution.getSchedules();
        List<Depot> depots = solution.getDepots();
        Graph graph = solution.getGraph();
        if (schedules == null || depots == null || graph == null) {
            return merged;
        }
        for (int scheduleIndex = 0; scheduleIndex < schedules.size(); scheduleIndex++) {
            Schedule schedule = schedules.get(scheduleIndex);
            List<Patient> nodes = resolveChain(schedules, scheduleIndex, chainOverrideByScheduleIndex);
            Map<Integer, List<WheelchairDepotBalanceChange>> byDepot =
                    WheelchairDepotEdgeRules.collectBalanceChangesForChainNodes(
                            nodes, schedule.getPorter(), depots, graph);
            for (Map.Entry<Integer, List<WheelchairDepotBalanceChange>> e : byDepot.entrySet()) {
                merged.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(e.getValue());
            }
        }
        return merged;
    }

    private static ViolationSweepOutcome zerosPerDepot(List<Depot> depots) {
        Map<Integer, Long> byDepotId = new LinkedHashMap<>();
        for (Depot depot : depots) {
            byDepotId.put(depot.getId(), 0L);
        }
        return new ViolationSweepOutcome(0L, Collections.unmodifiableMap(byDepotId));
    }

    /**
     * Start of the half-open interval {@code [start, end)} used to count seconds with negative balance.
     * <p>
     * While the simulator clock is still within (or before) the last scheduled second, the window begins at
     * {@code max(shift start, clock)} so online iterations only penalize inventory from “now” onward. When the clock
     * moves past the last job end (e.g. terminal sentinel at end-of-day), there is no forward interval left; in that
     * case the realized route interval {@code [shift start, last job end]} is used so the penalty matches the full
     * committed horizon instead of an empty backward range.
     */
    public static int evaluationWindowStartSeconds(Solution solution) {
        int sim = solution.getSimulatorClock().getSeconds();
        int rawEndExclusive = evaluationWindowEndExclusiveSeconds(solution, null);
        if (rawEndExclusive <= DomainConstants.SCHEDULE_START_TIME_SECONDS) {
            return Math.max(DomainConstants.SCHEDULE_START_TIME_SECONDS, sim);
        }
        int lastScheduledSecondIncluded = rawEndExclusive - 1;
        if (sim > lastScheduledSecondIncluded) {
            return DomainConstants.SCHEDULE_START_TIME_SECONDS;
        }
        return Math.max(DomainConstants.SCHEDULE_START_TIME_SECONDS, sim);
    }

    public static int evaluationWindowEndExclusiveSeconds(Solution solution) {
        return evaluationWindowEndExclusiveSeconds(solution, null);
    }

    public static int evaluationWindowEndExclusiveSeconds(
            Solution solution, List<List<Patient>> chainOverrideByScheduleIndex) {
        int maxEnd = DomainConstants.SCHEDULE_START_TIME_SECONDS;
        List<Schedule> schedules = solution.getSchedules();
        if (schedules == null) {
            return maxEnd + 1;
        }
        for (int i = 0; i < schedules.size(); i++) {
            List<Patient> nodes = resolveChain(schedules, i, chainOverrideByScheduleIndex);
            if (nodes.isEmpty()) {
                continue;
            }
            Patient tail = nodes.get(nodes.size() - 1);
            if (tail.getEndTime() != null) {
                maxEnd = Math.max(maxEnd, tail.getEndTime().getSeconds());
            }
        }
        return maxEnd + 1;
    }

    private static List<Patient> resolveChain(
            List<Schedule> schedules, int scheduleIndex, List<List<Patient>> chainOverrideByScheduleIndex) {
        if (chainOverrideByScheduleIndex != null
                && scheduleIndex < chainOverrideByScheduleIndex.size()
                && chainOverrideByScheduleIndex.get(scheduleIndex) != null) {
            return chainOverrideByScheduleIndex.get(scheduleIndex);
        }
        return nodesFromSchedule(schedules.get(scheduleIndex));
    }

    private static List<Patient> nodesFromSchedule(Schedule schedule) {
        List<Patient> nodes = new ArrayList<>();
        if (schedule == null || schedule.getStart() == null) {
            return nodes;
        }
        for (Patient patient = schedule.getStart(); patient != null; patient = patient.getNext()) {
            nodes.add(patient);
        }
        return nodes;
    }

    private static long violationSecondsForDepot(
            long balanceBeforeFirstWindowSecond,
            List<WheelchairDepotBalanceChange> inWindowChanges,
            int windowStart,
            int windowEndExclusive) {
        Map<Integer, Integer> netChangeAtSecond = new HashMap<>();
        for (WheelchairDepotBalanceChange c : inWindowChanges) {
            netChangeAtSecond.merge(c.timeSeconds(), c.wheelchairDelta(), Integer::sum);
        }
        long balance = balanceBeforeFirstWindowSecond;
        long violationSeconds = 0;
        for (int t = windowStart; t < windowEndExclusive; t++) {
            balance += netChangeAtSecond.getOrDefault(t, 0);
            if (balance < 0) {
                violationSeconds++;
            }
        }
        return violationSeconds;
    }
}
