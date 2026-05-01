package com.paper2.domain.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.paper2.domain.Depot;
import com.paper2.domain.DomainConstants;
import com.paper2.domain.FinalSchedule;
import com.paper2.domain.Graph;
import com.paper2.domain.Patient;
import com.paper2.domain.Porter;
import com.paper2.domain.Schedule;
import com.paper2.domain.Solution;
import com.paper2.domain.TimeObject;
import com.paper2.metrics.inventory.WheelchairDepotEdgeRules;
import com.paper2.metrics.inventory.WheelchairDepotEdgeRules.WheelchairDepotBalanceChange;

/**
 * Persistent wheelchair inventory: committed timeline from {@link FinalSchedule} merges, and per-iteration working
 * deltas on top of the committed balance at {@link #iterationAnchorSeconds}.
 */
public final class SolutionInventoryState {

    private final Map<Integer, DepotBalanceTimeline> finalCommittedByDepotId = new HashMap<>();
    private final Map<Integer, DepotBalanceTimeline> workingIterationByDepotId = new HashMap<>();
    /** Simulator clock (seconds) at the start of the current policy iteration, before {@link com.paper2.simulator.Solver#solve}. */
    private int iterationAnchorSeconds = DomainConstants.SCHEDULE_START_TIME_SECONDS;

    public SolutionInventoryState(List<Depot> depots) {
        if (depots != null) {
            for (Depot d : depots) {
                if (d != null) {
                    finalCommittedByDepotId.put(d.getId(), new DepotBalanceTimeline(d.getInitialWheelchairInventory()));
                    workingIterationByDepotId.put(d.getId(), new DepotBalanceTimeline(0));
                }
            }
        }
    }

    public int getIterationAnchorSeconds() {
        return iterationAnchorSeconds;
    }

    public void setIterationAnchorSeconds(int iterationAnchorSeconds) {
        this.iterationAnchorSeconds = iterationAnchorSeconds;
    }

    public Map<Integer, DepotBalanceTimeline> getFinalCommittedByDepotId() {
        return finalCommittedByDepotId;
    }

    public Map<Integer, DepotBalanceTimeline> getWorkingIterationByDepotId() {
        return workingIterationByDepotId;
    }

    /**
     * Committed wheelchair stock at {@code second} from merged final-schedule events only.
     */
    public long finalCommittedBalanceAt(int depotId, int second) {
        DepotBalanceTimeline t = finalCommittedByDepotId.get(depotId);
        return t == null ? 0L : t.balanceAtOrBefore(second);
    }

    /**
     * Total wheelchair stock at {@code second} within the working horizon model: committed stock at
     * {@link #iterationAnchorSeconds} plus working-route deltas with arrival in {@code [iterationAnchorSeconds, second]}.
     */
    public long totalBalanceAtInWorkingHorizon(int depotId, int second) {
        long base = finalCommittedBalanceAt(depotId, iterationAnchorSeconds);
        DepotBalanceTimeline w = workingIterationByDepotId.get(depotId);
        if (w == null) {
            return base;
        }
        long extra = 0;
        for (var e : w.getNetDeltaAtSecond().headMap(new TimeObject(second), true).entrySet()) {
            if (e.getKey().getSeconds() >= iterationAnchorSeconds) {
                extra += e.getValue();
            }
        }
        return base + extra;
    }

    /**
     * Rebuilds working deltas from current {@link Schedule} chains; arrivals outside
     * {@code [anchor, anchor + WORKING_INVENTORY_HORIZON)} are ignored.
     */
    public void rebuildWorkingFromWorkingSchedules(Solution solution) {
        int anchor = iterationAnchorSeconds;
        int horizonEndExclusive = anchor + DomainConstants.WORKING_INVENTORY_HORIZON_SECONDS;
        for (DepotBalanceTimeline w : workingIterationByDepotId.values()) {
            w.clearDeltas();
        }
        List<Depot> depots = solution.getDepots();
        Graph graph = solution.getGraph();
        if (solution.getSchedules() == null || depots == null || graph == null) {
            return;
        }
        for (Schedule schedule : solution.getSchedules()) {
            List<Patient> nodes = nodesFromSchedule(schedule);
            accumulateWorkingFromChain(nodes, schedule.getPorter(), depots, graph, anchor, horizonEndExclusive);
        }
    }

    private void accumulateWorkingFromChain(
            List<Patient> nodes,
            Porter porter,
            List<Depot> depots,
            Graph graph,
            int anchor,
            int horizonEndExclusive) {
        Map<Integer, List<WheelchairDepotBalanceChange>> byDepot =
                WheelchairDepotEdgeRules.collectBalanceChangesForChainNodes(nodes, porter, depots, graph);
        for (Map.Entry<Integer, List<WheelchairDepotBalanceChange>> e : byDepot.entrySet()) {
            DepotBalanceTimeline w = workingIterationByDepotId.get(e.getKey());
            if (w == null) {
                continue;
            }
            for (WheelchairDepotBalanceChange c : e.getValue()) {
                int t = c.timeSeconds();
                if (t >= anchor && t < horizonEndExclusive) {
                    w.addDelta(t, c.wheelchairDelta());
                }
            }
        }
    }

    private static List<Patient> nodesFromSchedule(Schedule schedule) {
        List<Patient> nodes = new ArrayList<>();
        if (schedule == null || schedule.getStart() == null) {
            return nodes;
        }
        for (Patient p = schedule.getStart(); p != null; p = p.getNext()) {
            nodes.add(p);
        }
        return nodes;
    }

    /**
     * Refreshes the committed (“final”) depot timelines from every {@link FinalSchedule} chain. This is the
     * post-iteration step that corresponds to extending committed inventory from merged final routes after the
     * simulator clock advances (equivalent to applying depot events from {@code FinalSchedule} only, without
     * double-counting, when the chains already contain the full merged history).
     * <p>
     * Events strictly before {@link DomainConstants#SCHEDULE_START_TIME_SECONDS} are ignored so stock at shift start
     * matches {@link com.paper2.domain.Depot#getInitialWheelchairInventory()} before any in-shift depot visit.
     */
    public void rebuildFinalCommittedFromAllFinalSchedules(Solution solution) {
        for (DepotBalanceTimeline fin : finalCommittedByDepotId.values()) {
            fin.clearDeltas();
        }
        List<Depot> depots = solution.getDepots();
        Graph graph = solution.getGraph();
        if (solution.getFinalSchedules() == null || depots == null || graph == null) {
            return;
        }
        for (FinalSchedule fs : solution.getFinalSchedules()) {
            List<Patient> pts = fs.getPatients();
            if (pts == null || pts.size() < 2) {
                continue;
            }
            Porter porter = fs.getPorter();
            Map<Integer, List<WheelchairDepotBalanceChange>> byDepot =
                    WheelchairDepotEdgeRules.collectBalanceChangesForChainNodes(pts, porter, depots, graph);
            for (Map.Entry<Integer, List<WheelchairDepotBalanceChange>> e : byDepot.entrySet()) {
                DepotBalanceTimeline fin = finalCommittedByDepotId.get(e.getKey());
                if (fin == null) {
                    continue;
                }
                for (WheelchairDepotBalanceChange c : e.getValue()) {
                    if (c.timeSeconds() < DomainConstants.SCHEDULE_START_TIME_SECONDS) {
                        continue;
                    }
                    fin.addDelta(c.timeSeconds(), c.wheelchairDelta());
                }
            }
        }
    }
}
