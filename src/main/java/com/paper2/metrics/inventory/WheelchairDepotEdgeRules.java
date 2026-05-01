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

    private WheelchairDepotEdgeRules() {}

    /**
     * Chooses the depot minimizing travel time from the previous patient's location.
     */
    public static Depot selectDepotForLeg(Patient previous, List<Depot> depots, Graph graph) {
        Depot best = null;
        int bestTravel = Integer.MAX_VALUE;
        if (depots == null || graph == null) {
            return null;
        }
        for (Depot depot : depots) {
            if (depot == null || depot.getLocation() == null) {
                continue;
            }
            int travel =
                    graph.getTravelTimeBetweenTwoLocations(previous.getLocation(), depot.getLocation())
                            .getSeconds();
            if (travel < bestTravel) {
                bestTravel = travel;
                best = depot;
            }
        }
        return best;
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
     */
    public static int netWheelchairDeltaAtDepotVisit(Patient previous, Patient current) {
        int delta = 0;
        if (requiresWheelchairPickupFromDepot(current)) {
            delta -= 1;
        }
        if (previous.isValid() && !previous.isDummy() && returnsWheelchairToDepotAfterService(previous)) {
            delta += 1;
        }
        return delta;
    }

    private static boolean requiresWheelchairPickupFromDepot(Patient patient) {
        MobilityAidPolicy policy = patient.getMobilityAidPolicy();
        return isWheelchairAid(policy) && !policy.isEquipmentPresentAtOrigin();
    }

    private static boolean returnsWheelchairToDepotAfterService(Patient previous) {
        MobilityAidPolicy policy = previous.getMobilityAidPolicy();
        return isWheelchairAid(policy) && !policy.isRetainEquipmentAtDestination();
    }

    private static boolean isWheelchairAid(MobilityAidPolicy policy) {
        if (policy == null || policy.getAidType() == null) {
            return false;
        }
        return policy.getAidType().toLowerCase().contains("wheelchair");
    }

    /**
     * Collects balance changes for consecutive patients in {@code nodes} (dummy head allowed), grouped by depot id.
     */
    public static Map<Integer, List<WheelchairDepotBalanceChange>> collectBalanceChangesForChainNodes(
            List<Patient> nodes, Porter porter, List<Depot> depots, Graph graph) {
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
            Depot depot = selectDepotForLeg(previous, depots, graph);
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
