package com.paper2.metrics.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.paper2.domain.Depot;
import com.paper2.domain.Graph;
import com.paper2.domain.MobilityAidPolicy;
import com.paper2.domain.Patient;
import com.paper2.domain.Porter;

/**
 * Centralizes wheelchair depot visit detection, depot choice, and net balance delta for one leg ({@code previous}
 * {@code ->} {@code current}) when the porter must visit a depot.
 */
public final class WheelchairDepotEdgeRules {

    /**
     * Resolves which depot applies on a route leg when aggregating inventory / violations (see
     * {@link com.paper2.metrics.inventory.DepotSelectionByObjective#resolver(DepotSelectionByObjective.DepotLegPlan, List, Graph)}).
     */
    @FunctionalInterface
    public interface DepotPerLeg {
        Depot depot(int scheduleIndex, int legIndex, Patient previous, Patient current);
    }

    private WheelchairDepotEdgeRules() {}

    /**
     * Depot visited on the leg {@code from} → {@code to} when the porter must stop at a depot; {@code 0} if
     * {@code to} is {@code null} or no depot visit applies (same rule as balance-change collection).
     */
    public static int depotIdVisitedBeforeNext(
            Patient from, Patient to, Porter porter, List<Depot> depots, Graph graph) {
        if (to == null || porter == null) {
            return 0;
        }
        MobilityAidPolicy p1 = from.getMobilityAidPolicy();
        MobilityAidPolicy p2 = to.getMobilityAidPolicy();
        if (p1 == null || p2 == null) {
            return 0;
        }
        if (!porter.shouldGoToDepot(p1, p2)) {
            return 0;
        }
        if (depots == null || graph == null) {
            return 0;
        }
        Depot depot = selectDepotForLegMinTravel(from, depots, graph);
        return depot == null ? 0 : depot.getId();
    }

    /**
     * Depot id for export / annotation when an optional per-leg selector is used (e.g. objective-aware choice).
     *
     * @param legIndex index of {@code to} in the schedule node list (1 = first edge after dummy)
     */
    public static int depotIdVisitedBeforeNext(
            Patient from,
            Patient to,
            Porter porter,
            List<Depot> depots,
            Graph graph,
            int scheduleIndex,
            int legIndex,
            DepotSelectionByObjective.DepotLegPlan plan) {
        if (to == null || porter == null) {
            return 0;
        }
        MobilityAidPolicy p1 = from.getMobilityAidPolicy();
        MobilityAidPolicy p2 = to.getMobilityAidPolicy();
        if (p1 == null || p2 == null) {
            return 0;
        }
        if (!porter.shouldGoToDepot(p1, p2)) {
            return 0;
        }
        if (depots == null || graph == null) {
            return 0;
        }
        Depot depot = DepotSelectionByObjective.depotForLeg(plan, scheduleIndex, legIndex, from, depots, graph);
        if (depot == null) {
            depot = selectDepotForLegMinTravel(from, depots, graph);
        }
        return depot.getId();
    }

    /** Minimum travel time from {@code previous}'s location (ties: lower depot id wins). */
    public static Depot selectDepotForLegMinTravel(Patient previous, List<Depot> depots, Graph graph) {
        if (previous == null || depots == null || graph == null) {
            return null;
        }
        Depot best = null;
        int bestTravel = Integer.MAX_VALUE;
        for (Depot depot : depots) {
            if (depot == null || depot.getLocation() == null) {
                continue;
            }
            int travel =
                    graph.getTravelTimeBetweenTwoLocations(previous.getLocation(), depot.getLocation())
                            .getSeconds();
            if (travel < bestTravel || (travel == bestTravel && (best == null || depot.getId() < best.getId()))) {
                bestTravel = travel;
                best = depot;
            }
        }
        return best;
    }

    /** @deprecated Prefer {@link #selectDepotForLegMinTravel}; kept for call sites that only minimize travel. */
    @Deprecated
    public static Depot selectDepotForLeg(Patient previous, List<Depot> depots, Graph graph) {
        return selectDepotForLegMinTravel(previous, depots, graph);
    }

    public static int arrivalSecondsAtDepot(Patient previous, Depot depot, Graph graph) {
        if (previous == null
                || previous.getEndTime() == null
                || depot == null
                || depot.getLocation() == null
                || graph == null) {
            return -1;
        }
        return previous.getEndTime().getSeconds()
                + graph.getTravelTimeBetweenTwoLocations(previous.getLocation(), depot.getLocation())
                        .getSeconds();
    }

    /**
     * Net wheelchair change at the depot visit between consecutive patients on a route (wheelchair pool only).
     * <p>
     * Callers pass consecutive nodes in <em>route list order</em> (e.g. {@link com.paper2.domain.FinalSchedule}
     * {@code patients}); do not use {@link Patient#isValid()} here — it reflects the working-chain {@code previous}
     * link, which is often cleared after {@link com.paper2.domain.Schedule#resetStartPatient} while the same
     * {@link Patient} instances remain on the final route list.
     */
    public static int netWheelchairDeltaAtDepotVisit(Patient previous, Patient current) {
        if (previous == null || current == null) {
            return 0;
        }
        MobilityAidPolicy policyPrevious = previous.getMobilityAidPolicy();
        MobilityAidPolicy policyCurrent = current.getMobilityAidPolicy();
        if (policyPrevious == null || policyCurrent == null) {
            return 0;
        }
        if (policyCurrent.isRequiresWheelchairPickupFromDepot(policyPrevious)) {
            return -1;
        }
        if (!previous.isDummy() && policyCurrent.isReturnsWheelchairToDepotAfterService(policyPrevious)) {
            return 1;
        }
        return 0;
    }

    /**
     * Collects balance changes for consecutive patients in {@code nodes} (dummy head allowed), grouped by depot id.
     */
    public static Map<Integer, List<WheelchairDepotBalanceChange>> collectBalanceChangesForChainNodes(
            List<Patient> nodes, Porter porter, List<Depot> depots, Graph graph) {
        return collectBalanceChangesForChainNodes(nodes, porter, depots, graph, -1, null);
    }

    /**
     * Same as {@link #collectBalanceChangesForChainNodes(List, Porter, List, Graph)} with per-leg depot resolution.
     * When {@code legDepot} is {@code null}, travel-minimizing depots are used.
     */
    public static Map<Integer, List<WheelchairDepotBalanceChange>> collectBalanceChangesForChainNodes(
            List<Patient> nodes,
            Porter porter,
            List<Depot> depots,
            Graph graph,
            int scheduleIndex,
            DepotPerLeg legDepot) {
        Map<Integer, List<WheelchairDepotBalanceChange>> byDepot = new HashMap<>();
        if (porter == null || nodes == null || nodes.size() < 2 || depots == null || graph == null) {
            return byDepot;
        }
        for (int index = 1; index < nodes.size(); index++) {
            Patient previous = nodes.get(index - 1);
            Patient current = nodes.get(index);
            MobilityAidPolicy previousPolicy = previous.getMobilityAidPolicy();
            MobilityAidPolicy currentPolicy = current.getMobilityAidPolicy();
            if (previousPolicy == null || currentPolicy == null) {
                continue;
            }
            if (!porter.shouldGoToDepot(previousPolicy, currentPolicy)) {
                continue;
            }
            Depot depot =
                    legDepot != null
                            ? legDepot.depot(scheduleIndex, index, previous, current)
                            : selectDepotForLegMinTravel(previous, depots, graph);
            if (depot == null) {
                depot = selectDepotForLegMinTravel(previous, depots, graph);
            }
            if (depot == null) {
                continue;
            }
            int arrivalSeconds = arrivalSecondsAtDepot(previous, depot, graph);
            if (arrivalSeconds < 0) {
                continue;
            }
            int netDelta = netWheelchairDeltaAtDepotVisit(previous, current);
            if (netDelta == 0) {
                continue;
            }
            byDepot
                    .computeIfAbsent(depot.getId(), k -> new ArrayList<>())
                    .add(new WheelchairDepotBalanceChange(arrivalSeconds, netDelta));
        }
        return byDepot;
    }

    public record WheelchairDepotBalanceChange(int timeSeconds, int wheelchairDelta) {}
}
