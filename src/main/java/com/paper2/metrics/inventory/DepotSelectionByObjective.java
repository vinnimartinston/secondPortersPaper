package com.paper2.metrics.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.paper2.domain.Depot;
import com.paper2.domain.Graph;
import com.paper2.domain.Patient;
import com.paper2.domain.Porter;
import com.paper2.domain.Schedule;
import com.paper2.domain.Solution;
import com.paper2.metrics.WheelchairDepotViolationSecondsCalculator;

/**
 * Chooses which depot is used on each route leg so as to minimize total seconds of negative wheelchair stock across
 * depots (the inventory term multiplied by {@link com.paper2.domain.Solution#getDepotInventoryViolationPenaltyCoefficient()}
 * in the combined objective). Legs with non-zero balance delta are decided in ascending order of arrival time at the
 * minimum-travel depot; earlier choices are fixed when scoring later legs.
 * <p>
 * Use {@link #buildPlan(Solution, List)} to obtain a {@link DepotLegPlan}, then {@link #resolver(DepotLegPlan, List, Graph)}
 * for {@link WheelchairDepotEdgeRules.DepotPerLeg} passed to violation and balance-change aggregation.
 */
public final class DepotSelectionByObjective {

    /** Immutable depot id per schedule leg key {@code scheduleIndex + ":" + legIndex}. */
    public record DepotLegPlan(Map<String, Integer> depotIdByScheduleAndLeg) {}

    private DepotSelectionByObjective() {}

    /**
     * List aligned with {@link Solution#getSchedules()} indices: each entry starts {@code null} so {@link #buildPlan}
     * uses the live chain for that schedule until overridden.
     */
    public static List<List<Patient>> emptyChainOverrides(int scheduleCount) {
        List<List<Patient>> list = new ArrayList<>(scheduleCount);
        for (int i = 0; i < scheduleCount; i++) {
            list.add(null);
        }
        return list;
    }

    /**
     * @param chainOverrideByScheduleIndex same convention as
     *     {@link WheelchairDepotViolationSecondsCalculator#totalViolationSecondsAcrossDepots(Solution, List)}:
     *     {@code null} entries mean use the live {@link Schedule} chain
     */
    public static DepotLegPlan buildPlan(Solution solution, List<List<Patient>> chainOverrideByScheduleIndex) {
        if (solution == null) {
            return new DepotLegPlan(Map.of());
        }
        List<Depot> depots = solution.getDepots();
        Graph graph = solution.getGraph();
        List<Schedule> schedules = solution.getSchedules();
        if (depots == null || depots.isEmpty() || graph == null || schedules == null) {
            return new DepotLegPlan(Map.of());
        }
        int n = schedules.size();
        Map<String, Integer> chosenId = new HashMap<>();
        List<LegWork> toDecide = new ArrayList<>();
        for (int scheduleIndex = 0; scheduleIndex < n; scheduleIndex++) {
            Schedule schedule = schedules.get(scheduleIndex);
            List<Patient> nodes =
                    WheelchairDepotViolationSecondsCalculator.resolveChainPublic(
                            solution, scheduleIndex, chainOverrideByScheduleIndex);
            Porter porter = schedule.getPorter();
            for (int legIndex = 1; legIndex < nodes.size(); legIndex++) {
                Patient previous = nodes.get(legIndex - 1);
                Patient current = nodes.get(legIndex);
                if (previous.getMobilityAidPolicy() == null || current.getMobilityAidPolicy() == null) {
                    continue;
                }
                if (!porter.shouldGoToDepot(
                        previous.getMobilityAidPolicy(), current.getMobilityAidPolicy())) {
                    continue;
                }
                Depot minTravelDepot = WheelchairDepotEdgeRules.selectDepotForLegMinTravel(previous, depots, graph);
                if (WheelchairDepotEdgeRules.netWheelchairDeltaAtDepotVisit(previous, current) == 0) {
                    if (minTravelDepot != null) {
                        chosenId.put(key(scheduleIndex, legIndex), minTravelDepot.getId());
                    }
                    continue;
                }
                int arrivalKey =
                        minTravelDepot == null
                                ? Integer.MAX_VALUE
                                : Math.max(
                                        0,
                                        WheelchairDepotEdgeRules.arrivalSecondsAtDepot(
                                                previous, minTravelDepot, graph));
                toDecide.add(new LegWork(scheduleIndex, legIndex, previous, current, arrivalKey));
            }
        }
        toDecide.sort(
                Comparator.comparingInt(LegWork::arrivalSortKey)
                        .thenComparingInt(LegWork::scheduleIndex)
                        .thenComparingInt(LegWork::legIndex));

        for (LegWork w : toDecide) {
            Depot best = null;
            long bestViol = Long.MAX_VALUE;
            for (Depot d : depots) {
                if (d == null || d.getLocation() == null) {
                    continue;
                }
                Depot candidate = d;
                WheelchairDepotEdgeRules.DepotPerLeg trial =
                        (sid, leg, prev, cur) -> {
                            if (sid == w.scheduleIndex && leg == w.legIndex) {
                                return candidate;
                            }
                            Integer fixedId = chosenId.get(key(sid, leg));
                            if (fixedId != null) {
                                return findDepotById(depots, fixedId);
                            }
                            return WheelchairDepotEdgeRules.selectDepotForLegMinTravel(prev, depots, graph);
                        };
                long v =
                        WheelchairDepotViolationSecondsCalculator.totalViolationSecondsAcrossDepots(
                                solution, chainOverrideByScheduleIndex, trial);
                if (v < bestViol || (v == bestViol && (best == null || d.getId() < best.getId()))) {
                    bestViol = v;
                    best = d;
                }
            }
            Depot resolved =
                    best != null
                            ? best
                            : WheelchairDepotEdgeRules.selectDepotForLegMinTravel(w.previous, depots, graph);
            if (resolved != null) {
                chosenId.put(key(w.scheduleIndex, w.legIndex), resolved.getId());
            }
        }
        return new DepotLegPlan(Collections.unmodifiableMap(new HashMap<>(chosenId)));
    }

    /** Resolves the depot for one leg from a plan, falling back to minimum travel when the plan has no entry. */
    public static Depot depotForLeg(
            DepotLegPlan plan,
            int scheduleIndex,
            int legIndex,
            Patient previous,
            List<Depot> depots,
            Graph graph) {
        if (previous == null || depots == null || graph == null) {
            return null;
        }
        if (plan != null) {
            Integer id = plan.depotIdByScheduleAndLeg().get(key(scheduleIndex, legIndex));
            if (id != null) {
                Depot d = findDepotById(depots, id);
                if (d != null) {
                    return d;
                }
            }
        }
        return WheelchairDepotEdgeRules.selectDepotForLegMinTravel(previous, depots, graph);
    }

    public static WheelchairDepotEdgeRules.DepotPerLeg resolver(
            DepotLegPlan plan, List<Depot> depots, Graph graph) {
        return (scheduleIndex, legIndex, previous, current) ->
                depotForLeg(plan, scheduleIndex, legIndex, previous, depots, graph);
    }

    /**
     * One entry per {@link Solution#getSchedules()} index: {@code patients} from the {@link FinalSchedule} whose
     * porter id matches that index, or {@code null} if none.
     */
    public static List<List<Patient>> chainOverridesFromFinalSchedulesByPorterId(Solution solution) {
        int n = solution.getSchedules() != null ? solution.getSchedules().size() : 0;
        List<List<Patient>> chains = new ArrayList<>(Collections.nCopies(n, null));
        if (solution.getFinalSchedules() == null) {
            return chains;
        }
        for (var fs : solution.getFinalSchedules()) {
            if (fs.getPorter() == null) {
                continue;
            }
            int id = fs.getPorter().getId();
            if (id >= 0 && id < n) {
                chains.set(id, fs.getPatients());
            }
        }
        return chains;
    }

    private static Depot findDepotById(List<Depot> depots, int id) {
        for (Depot d : depots) {
            if (d != null && d.getId() == id) {
                return d;
            }
        }
        return null;
    }

    private record LegWork(
            int scheduleIndex, int legIndex, Patient previous, Patient current, int arrivalSortKey) {}

    private static String key(int scheduleIndex, int legIndex) {
        return scheduleIndex + ":" + legIndex;
    }
}
