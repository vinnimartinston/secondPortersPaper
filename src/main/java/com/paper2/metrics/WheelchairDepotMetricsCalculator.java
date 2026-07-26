package com.paper2.metrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.paper2.domain.Depot;
import com.paper2.domain.DomainConstants;
import com.paper2.domain.FinalSchedule;
import com.paper2.domain.Graph;
import com.paper2.domain.MobilityAidPolicy;
import com.paper2.domain.Patient;
import com.paper2.domain.Porter;
import com.paper2.domain.Solution;
import com.paper2.dto.metrics.ExperimentMetricsDto.WheelchairDepot;
import com.paper2.metrics.inventory.DepotSelectionByObjective;
import com.paper2.metrics.inventory.WheelchairDepotEdgeRules;

/**
 * Wheelchair / depot KPIs aligned with {@code WC_indicator} and {@code print_chairs_infos} in legacy
 * {@code printer.cpp} (lines ~682–866), using final routes and the same per-leg depot policy as solution export.
 */
public final class WheelchairDepotMetricsCalculator {

    private static final int HORIZON_TAIL_PADDING_SECONDS = 10;

    private WheelchairDepotMetricsCalculator() {}

    public static WheelchairDepot compute(Solution solution) {
        WheelchairDepot dto = new WheelchairDepot();
        if (solution == null
                || solution.getFinalSchedules() == null
                || solution.getDepots() == null
                || solution.getDepots().isEmpty()
                || solution.getGraph() == null) {
            return dto;
        }

        List<Depot> depots = new ArrayList<>(solution.getDepots());
        depots.sort(Comparator.comparingInt(Depot::getId));
        Graph graph = solution.getGraph();

        int makespanSec = 0;
        for (FinalSchedule fs : solution.getFinalSchedules()) {
            if (fs.getPatients() == null) {
                continue;
            }
            for (Patient p : fs.getPatients()) {
                if (p != null && !p.isDummy() && p.getEndTime() != null) {
                    makespanSec = Math.max(makespanSec, p.getEndTime().getSeconds());
                }
            }
        }
        int shiftStart = DomainConstants.SCHEDULE_START_TIME_SECONDS;
        int horizonSize = Math.max(0, makespanSec - shiftStart + HORIZON_TAIL_PADDING_SECONDS);
        if (horizonSize == 0) {
            return dto;
        }

        int[] chairsInSystem = new int[horizonSize];
        int[][] depotsInTime = new int[depots.size()][horizonSize];
        double[] pickUp = new double[depots.size()];
        double[] dropOff = new double[depots.size()];
        double[] walkingToDepot = new double[depots.size()];

        DepotSelectionByObjective.DepotLegPlan plan =
                DepotSelectionByObjective.buildPlan(
                        solution, DepotSelectionByObjective.chainOverridesFromFinalSchedulesByPorterId(solution));
        WheelchairDepotEdgeRules.DepotPerLeg legDepot =
                DepotSelectionByObjective.resolver(plan, depots, graph);

        for (FinalSchedule fs : solution.getFinalSchedules()) {
            List<Patient> pts = fs.getPatients();
            if (pts == null || pts.size() < 2) {
                continue;
            }
            Porter porter = fs.getPorter();
            int scheduleIndex = porter != null ? porter.getId() : -1;

            for (int i = 1; i < pts.size(); i++) {
                Patient previous = pts.get(i - 1);
                Patient current = pts.get(i);
                if (current.isDummy()) {
                    continue;
                }
                int endRel = toRelativeSecond(current.getEndTime().getSeconds(), shiftStart);
                int travelSec = current.getTravelTime().getSeconds();
                int transportSec = current.getTransportTime();
                int startRel = endRel - travelSec - transportSec + 1;

                Depot depot =
                        legDepot != null
                                ? legDepot.depot(scheduleIndex, i, previous, current)
                                : WheelchairDepotEdgeRules.selectDepotForLegMinTravel(previous, depots, graph);
                int depotIdx = depotIndex(depots, depot);

                applyTransition(
                        previous,
                        current,
                        porter,
                        depot,
                        depotIdx,
                        startRel,
                        endRel,
                        travelSec,
                        graph,
                        chairsInSystem,
                        depotsInTime,
                        pickUp,
                        dropOff,
                        walkingToDepot);
            }

            Patient last = pts.get(pts.size() - 1);
            if (!last.isDummy()) {
                applyLastJobReturn(last, depots, graph, shiftStart, depotsInTime, dropOff);
            }
        }

        int[] chairsInUse = new int[horizonSize];
        for (int t = 0; t < horizonSize; t++) {
            for (int d = 0; d < depots.size(); d++) {
                chairsInUse[t] -= depotsInTime[d][t];
            }
        }

        int wcMax = 0;
        for (int value : chairsInUse) {
            wcMax = Math.max(wcMax, value);
        }
        int peakSeconds = 0;
        for (int value : chairsInUse) {
            if (value == wcMax) {
                peakSeconds++;
            }
        }
        double wcMaxUsageFraction = horizonSize > 0 ? peakSeconds / (double) horizonSize : 0.0;

        List<Double> depotMinBalance = new ArrayList<>();
        for (int d = 0; d < depots.size(); d++) {
            int min = 0;
            for (int t = 0; t < horizonSize; t++) {
                min = Math.min(min, depotsInTime[d][t]);
            }
            depotMinBalance.add((double) (-min));
        }

        double totalPickUp = 0;
        double totalDropOff = 0;
        double totalWalking = 0;
        for (int d = 0; d < depots.size(); d++) {
            totalPickUp += pickUp[d];
            totalDropOff += dropOff[d];
            totalWalking += walkingToDepot[d];
        }

        double visitCount = totalPickUp + totalDropOff;
        double avgWalkingToDepotMinutes =
                visitCount > 0 ? (totalWalking / visitCount) / 60.0 : 0.0;

        dto.setMaxWheelchairsUsedAtSameTime(wcMax);
        dto.setTimeShareUsingMaxWheelchairs(wcMaxUsageFraction);
        dto.setDepotMinBalance(depotMinBalance);
        dto.setTotalPickUp(totalPickUp);
        dto.setTotalDropOff(totalDropOff);
        dto.setAvgWalkingToDepotMinutes(avgWalkingToDepotMinutes);
        return dto;
    }

    private static void applyLastJobReturn(
            Patient last,
            List<Depot> depots,
            Graph graph,
            int shiftStart,
            int[][] depotsInTime,
            double[] dropOff) {
        MobilityAidPolicy policy = last.getMobilityAidPolicy();
        if (policy == null || !policy.isWheelchairAid()) {
            return;
        }
        WcModalClass modal = classify(policy);
        if (modal != WcModalClass.WC_PICKUP_DEPOT && modal != WcModalClass.WC_RETURN) {
            return;
        }
        Depot depot = WheelchairDepotEdgeRules.selectDepotForLegMinTravel(last, depots, graph);
        if (depot == null || last.getEndTime() == null || last.getLocation() == null) {
            return;
        }
        int endRel = toRelativeSecond(last.getEndTime().getSeconds(), shiftStart);
        int travelToDepot =
                graph.getTravelTimeBetweenTwoLocations(last.getLocation(), depot.getLocation()).getSeconds();
        int returnStart = endRel + travelToDepot;
        int depotIdx = depotIndex(depots, depot);
        if (depotIdx >= 0) {
            applyFrom(depotsInTime[depotIdx], returnStart, 1);
            dropOff[depotIdx]++;
        }
    }

    private static void applyTransition(
            Patient previous,
            Patient current,
            Porter porter,
            Depot depot,
            int depotIdx,
            int startRel,
            int endRel,
            int travelSec,
            Graph graph,
            int[] chairsInSystem,
            int[][] depotsInTime,
            double[] pickUp,
            double[] dropOff,
            double[] walkingToDepot) {
        MobilityAidPolicy prevPolicy = previous.getMobilityAidPolicy();
        MobilityAidPolicy curPolicy = current.getMobilityAidPolicy();
        if (prevPolicy == null || curPolicy == null || porter == null) {
            return;
        }

        WcModalClass prevModal = classify(prevPolicy);
        WcModalClass curModal = classify(curPolicy);
        int depotArrivalRel = depotArrivalRelative(previous, depot, graph, startRel, travelSec);

        if (prevModal == WcModalClass.BED_OR_NONE) {
            if (curModal == WcModalClass.WC_PICKUP_DEPOT || curModal == WcModalClass.WC_AT_ORIGIN) {
                if (depotIdx >= 0 && porter.shouldGoToDepot(prevPolicy, curPolicy)) {
                    applyFrom(depotsInTime[depotIdx], depotArrivalRel, -1);
                    pickUp[depotIdx]++;
                    walkingToDepot[depotIdx] += travelSec;
                }
            }
            if (curModal == WcModalClass.WC_AT_ORIGIN || curModal == WcModalClass.WC_PICKUP_DEPOT) {
                applyFrom(chairsInSystem, endRel + 1, 1);
            }
            if (curModal == WcModalClass.WC_RETURN) {
                applyFrom(chairsInSystem, endRel + 1, -1);
            }
        } else if (prevModal == WcModalClass.WC_PICKUP_DEPOT || prevModal == WcModalClass.WC_RETURN) {
            if (curModal == WcModalClass.BED_OR_NONE) {
                if (depotIdx >= 0 && porter.shouldGoToDepot(prevPolicy, curPolicy)) {
                    applyFrom(depotsInTime[depotIdx], depotArrivalRel + 1, 1);
                    dropOff[depotIdx]++;
                    walkingToDepot[depotIdx] += travelSec;
                }
            } else if (curModal == WcModalClass.WC_AT_ORIGIN || curModal == WcModalClass.WC_PICKUP_DEPOT) {
                applyFrom(chairsInSystem, endRel + 1, 1);
            } else if (curModal == WcModalClass.WC_RETURN) {
                applyFrom(chairsInSystem, endRel + 1, -1);
                if (depotIdx >= 0 && porter.shouldGoToDepot(prevPolicy, curPolicy)) {
                    applyFrom(depotsInTime[depotIdx], depotArrivalRel + 1, 1);
                    dropOff[depotIdx]++;
                    walkingToDepot[depotIdx] += travelSec;
                }
            }
        } else if (prevModal == WcModalClass.WC_AT_ORIGIN) {
            if (curModal == WcModalClass.WC_PICKUP_DEPOT || curModal == WcModalClass.WC_AT_ORIGIN) {
                if (depotIdx >= 0 && porter.shouldGoToDepot(prevPolicy, curPolicy)) {
                    applyFrom(depotsInTime[depotIdx], depotArrivalRel, -1);
                    pickUp[depotIdx]++;
                    walkingToDepot[depotIdx] += travelSec;
                }
            }
            if (curModal == WcModalClass.WC_AT_ORIGIN || curModal == WcModalClass.WC_PICKUP_DEPOT) {
                applyFrom(chairsInSystem, endRel + 1, 1);
            }
            if (curModal == WcModalClass.WC_RETURN) {
                applyFrom(chairsInSystem, endRel + 1, -1);
            }
        }
    }

    private static int depotArrivalRelative(
            Patient previous, Depot depot, Graph graph, int startRel, int travelSec) {
        if (depot != null && previous.getEndTime() != null && previous.getLocation() != null && graph != null) {
            int arrival = WheelchairDepotEdgeRules.arrivalSecondsAtDepot(previous, depot, graph);
            if (arrival >= 0) {
                return toRelativeSecond(arrival, DomainConstants.SCHEDULE_START_TIME_SECONDS);
            }
        }
        int setupToDepot = Math.max(0, travelSec / 2);
        return startRel + setupToDepot;
    }

    private static int toRelativeSecond(int absoluteSecond, int shiftStart) {
        return absoluteSecond - shiftStart;
    }

    private static void applyFrom(int[] series, int fromIndex, int delta) {
        if (delta == 0) {
            return;
        }
        int start = Math.max(0, fromIndex);
        for (int t = start; t < series.length; t++) {
            series[t] += delta;
        }
    }

    private static int depotIndex(List<Depot> depots, Depot depot) {
        if (depot == null) {
            return -1;
        }
        for (int i = 0; i < depots.size(); i++) {
            if (depots.get(i).getId() == depot.getId()) {
                return i;
            }
        }
        return -1;
    }

    private static WcModalClass classify(MobilityAidPolicy policy) {
        if (policy.isHospitalBedAid() || !policy.isWheelchairAid()) {
            return WcModalClass.BED_OR_NONE;
        }
        if (policy.isEquipmentPresentAtOrigin()) {
            return WcModalClass.WC_AT_ORIGIN;
        }
        if (policy.isRetainEquipmentAtDestination()) {
            return WcModalClass.WC_PICKUP_DEPOT;
        }
        return WcModalClass.WC_RETURN;
    }

    private enum WcModalClass {
        BED_OR_NONE,
        WC_PICKUP_DEPOT,
        WC_AT_ORIGIN,
        WC_RETURN
    }
}
