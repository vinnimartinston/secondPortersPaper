package com.paper2.domain;

/**
 * Shared scheduling instants and per-leg travel along the porter route (domain-only; uses {@link Graph} for matrix
 * lookups).
 */
public final class RouteTiming {

    private RouteTiming() {}

    /**
     * First second at which the next job may start: {@code max(requested, previousEnd)}; when {@code simulatorClock}
     * is non-null, uses {@code max(requested, simulator, previousEnd)} (same rule as {@link Solution#removeIdle()}).
     */
    public static TimeObject earliestStart(
            TimeObject requested, TimeObject previousEnd, TimeObject simulatorClock) {
        int reqSec = requested != null ? requested.getSeconds() : 0;
        int prevSec = previousEnd != null ? previousEnd.getSeconds() : 0;
        if (simulatorClock == null) {
            return new TimeObject(Math.max(reqSec, prevSec));
        }
        int simSec = simulatorClock.getSeconds();
        return new TimeObject(Math.max(Math.max(reqSec, simSec), prevSec));
    }

    /**
     * Travel duration from {@code prev} to {@code cur}: direct leg on the graph, or via {@code depotIfVisit} when
     * {@link Porter#shouldGoToDepot} applies and {@code depotIfVisit} is non-null.
     */
    public static TimeObject travelBetweenPatients(
            Graph graph, Patient prev, Patient cur, Porter porter, Depot depotIfVisit) {
        if (graph == null || prev == null || cur == null) {
            return new TimeObject(0);
        }
        MobilityAidPolicy p1 = prev.getMobilityAidPolicy();
        MobilityAidPolicy p2 = cur.getMobilityAidPolicy();
        if (porter != null
                && p1 != null
                && p2 != null
                && porter.shouldGoToDepot(p1, p2)
                && depotIfVisit != null) {
            return graph.getTravelTimeBetweenTwoLocations(
                    prev.getLocation(), cur.getLocation(), true, depotIfVisit);
        }
        return graph.getTravelTimeBetweenTwoLocations(prev.getLocation(), cur.getLocation());
    }
}
