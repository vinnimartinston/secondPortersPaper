package com.paper2.metrics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.paper2.domain.DomainConstants;
import com.paper2.domain.FinalSchedule;
import com.paper2.domain.Graph;
import com.paper2.domain.Patient;
import com.paper2.domain.Solution;
import com.paper2.domain.TimeObject;
import com.paper2.domain.TransportModeKind;
import com.paper2.dto.InputDto;
import com.paper2.dto.metrics.ExperimentMetricsDto;
import com.paper2.dto.metrics.ExperimentMetricsDto.PorterEffort;
import com.paper2.dto.metrics.ExperimentMetricsDto.PorterEffortRow;
import com.paper2.dto.metrics.ExperimentMetricsDto.PorterScheduleTimeRow;
import com.paper2.dto.metrics.ExperimentMetricsDto.PriorityBreakdown;
import com.paper2.dto.metrics.ExperimentMetricsDto.Response;
import com.paper2.dto.metrics.ExperimentMetricsDto.ResponseByPriority;
import com.paper2.dto.metrics.ExperimentMetricsDto.ScheduleTimeAggregates;
import com.paper2.dto.metrics.ExperimentMetricsDto.SetupIdle;
import com.paper2.dto.metrics.ExperimentMetricsDto.Summary;
import com.paper2.dto.metrics.ExperimentMetricsDto.TimeSecondsAndClock;
import com.paper2.dto.metrics.ExperimentMetricsDto.Tardiness;
import com.paper2.dto.metrics.ExperimentMetricsDto.WheelchairDepot;

/**
 * Computes aggregated metrics from {@link Solution#getFinalSchedules()} for {@code *_metrics.json},
 * aligned with the summary in {@code print_resume_results} / {@code print_final_results} in
 * {@code printer.cpp}.
 */
public final class SolutionMetricsCalculator {

    private SolutionMetricsCalculator() {}

    /**
     * Builds the experiment metrics DTO: tardiness, response, idle, per-porter effort, per-route time
     * aggregates, etc.
     *
     * @param solution final solution; may be {@code null} or without schedules (returns empty structure)
     * @param input input instance (name and porter count); may be {@code null}
     * @param simulatorWallTimeSeconds wall-clock time of {@code Simulator#start}, in seconds
     * @return metrics ready for Jackson serialization
     */
    public static ExperimentMetricsDto compute(
            Solution solution, InputDto input, double simulatorWallTimeSeconds) {
        ExperimentMetricsDto dto = new ExperimentMetricsDto();
        dto.setSimulatorWallTimeSeconds(simulatorWallTimeSeconds);
        if (input != null) {
            dto.setInstanceName(input.getName());
            dto.setAmountOfPorters(input.getAmountOfPorters());
        }
        if (solution == null || solution.getFinalSchedules() == null) {
            dto.setTransportedPatientCount(0);
            dto.setSummary(new Summary(0, "00:00:00", 0, 0, 0));
            dto.setTardiness(new Tardiness(0, 0, 0, new ArrayList<>(), 0));
            dto.setResponse(new Response(new ArrayList<>(), 0));
            dto.setSetupIdle(new SetupIdle(0, 0, 0, "00:00:00", 0, 0, 0, null));
            dto.setPorterEffort(new PorterEffort(0, 0, 0, new ArrayList<>()));
            dto.setWheelchairDepot(new WheelchairDepot());
            dto.setScheduleTimeAggregates(emptyScheduleTimeAggregates());
            return dto;
        }

        List<Patient> patients = collectPatients(solution);
        dto.setTransportedPatientCount(patients.size());
        int n = patients.size();

        int makespanSec = 0;
        double sumWeightedTard = 0;
        double sumUnweightedTard = 0;
        double sumTravelSetup = 0;
        double sumIdle = 0;
        int tard0 = 0;
        int tardLe = 0;
        int tardGt = 0;

        Map<Integer, Agg> byPri = new HashMap<>();

        for (Patient p : patients) {
            int end = p.getEndTime().getSeconds();
            makespanSec = Math.max(makespanSec, end);

            int lateSec = p.getTime().getLateness().getSeconds();
            int w = p.getPriority().getWeight();
            sumWeightedTard += (double) lateSec * w;
            sumUnweightedTard += lateSec;
            sumTravelSetup += p.getTravelTime().getSeconds();

            if (lateSec == 0) {
                tard0++;
            } else if (lateSec <= 1800) {
                tardLe++;
            } else {
                tardGt++;
            }

            int pri = p.getPriority().getPriority();
            Agg a = byPri.computeIfAbsent(pri, k -> new Agg());
            a.patientCount++;
            a.sumTardUnweighted += lateSec;
            if (lateSec > 0) {
                a.tardyPatientCount++;
            }
            a.sumResponseSec += responseSeconds(p);
        }

        Graph graph = solution.getGraph();
        String idleNote = null;
        if (graph == null) {
            idleNote = "Idle not computed: Solution has no Graph.";
        } else {
            for (FinalSchedule fs : solution.getFinalSchedules()) {
                sumIdle += idleSecondsReplayedOnRoute(fs, graph);
            }
        }

        int horizon = Math.max(0, makespanSec - DomainConstants.SCHEDULE_START_TIME_SECONDS);
        int numPorters =
                solution.getFinalSchedules() != null ? solution.getFinalSchedules().size() : 0;
        double avgIdleMinPerPorter =
                numPorters > 0 ? (sumIdle / 60.0) / numPorters : 0;
        double horizonMin = horizon / 60.0;
        double idleShare = horizonMin > 0 ? avgIdleMinPerPorter / horizonMin : 0;

        Summary summary =
                new Summary(
                        makespanSec,
                        formatClock(new TimeObject(makespanSec)),
                        sumWeightedTard,
                        sumUnweightedTard,
                        solution.getObjectiveValue());

        List<PriorityBreakdown> tardPri = new ArrayList<>();
        List<ResponseByPriority> respPri = new ArrayList<>();
        double meanTardMinAll = n > 0 ? sumUnweightedTard / 60.0 / n : 0;
        double meanRespMinAll = 0;
        if (n > 0) {
            double sumResp =
                    patients.stream().mapToDouble(SolutionMetricsCalculator::responseSeconds).sum();
            meanRespMinAll = sumResp / 60.0 / n;
        }

        List<Integer> priKeys = new ArrayList<>(byPri.keySet());
        priKeys.sort(Integer::compareTo);
        for (Integer pri : priKeys) {
            Agg a = byPri.get(pri);
            double avgTardMin =
                    a.patientCount > 0 ? a.sumTardUnweighted / 60.0 / a.patientCount : 0;
            double share =
                    a.patientCount > 0 ? a.tardyPatientCount / (double) a.patientCount : 0;
            double avgRespMin =
                    a.patientCount > 0 ? a.sumResponseSec / 60.0 / a.patientCount : 0;
            tardPri.add(
                    new PriorityBreakdown(
                            pri, a.patientCount, avgTardMin, a.tardyPatientCount, share));
            respPri.add(new ResponseByPriority(pri, a.patientCount, avgRespMin));
        }

        Tardiness tardiness =
                new Tardiness(tard0, tardLe, tardGt, tardPri, meanTardMinAll);
        Response response = new Response(respPri, meanRespMinAll);

        SetupIdle setupIdle =
                new SetupIdle(
                        sumTravelSetup,
                        n > 0 ? sumTravelSetup / 60.0 / n : 0,
                        sumIdle,
                        formatDurationSeconds(sumIdle),
                        avgIdleMinPerPorter,
                        idleShare,
                        horizon,
                        idleNote);

        PorterEffort pe = buildPorterEffort(solution);

        dto.setSummary(summary);
        dto.setTardiness(tardiness);
        dto.setResponse(response);
        dto.setSetupIdle(setupIdle);
        dto.setPorterEffort(pe);
        dto.setWheelchairDepot(new WheelchairDepot());
        dto.setScheduleTimeAggregates(buildScheduleTimeAggregates(solution));
        return dto;
    }

    private static ScheduleTimeAggregates emptyScheduleTimeAggregates() {
        ScheduleTimeAggregates a = new ScheduleTimeAggregates();
        a.setMinDurationActiveSeconds(-1);
        a.setMinDurationActiveClock(null);
        return a;
    }

    private static ScheduleTimeAggregates buildScheduleTimeAggregates(Solution solution) {
        ScheduleTimeAggregates agg = new ScheduleTimeAggregates();
        if (solution.getFinalSchedules() == null) {
            return emptyScheduleTimeAggregates();
        }
        long sumTravel = 0;
        long sumTransport = 0;
        long sumIdle = 0;
        long sumDuration = 0;
        long sumDurationActive = 0;
        int maxDuration = 0;
        int minDurationActive = Integer.MAX_VALUE;
        int active = 0;
        int idle = 0;
        int allChecks = 0;
        int n = solution.getFinalSchedules().size();
        List<PorterScheduleTimeRow> rows = new ArrayList<>();

        for (FinalSchedule fs : solution.getFinalSchedules()) {
            int porterId = fs.getPorter() != null ? fs.getPorter().getId() : -1;
            PorterScheduleRouteMetrics m = PorterScheduleRouteMetrics.compute(solution, fs, porterId);

            sumTravel += m.sumTravelSeconds();
            sumTransport += m.sumTransportSeconds();
            sumIdle += m.idleSeconds();
            sumDuration += m.durationSeconds();

            boolean allTrue =
                    m.lastPatientEndMatchesScheduleEnd()
                            && m.lastEndMinusDummyEndEqualsDuration()
                            && m.travelTransportIdleSumEqualsDuration();
            if (allTrue) {
                allChecks++;
            }

            if (m.realPatientCount() > 0) {
                active++;
                sumDurationActive += m.durationSeconds();
                maxDuration = Math.max(maxDuration, m.durationSeconds());
                minDurationActive = Math.min(minDurationActive, m.durationSeconds());
            } else {
                idle++;
                maxDuration = Math.max(maxDuration, m.durationSeconds());
            }

            rows.add(
                    new PorterScheduleTimeRow(
                            porterId,
                            m.sumTravelSeconds(),
                            ScheduleTimeFormat.durationHms(m.sumTravelSeconds()),
                            m.sumTransportSeconds(),
                            ScheduleTimeFormat.durationHms(m.sumTransportSeconds()),
                            m.idleSeconds(),
                            ScheduleTimeFormat.durationHmsSigned(m.idleSeconds()),
                            m.durationSeconds(),
                            ScheduleTimeFormat.durationHms(Math.max(0, m.durationSeconds())),
                            m.realPatientCount(),
                            allTrue));
        }

        agg.setSumTravel(new TimeSecondsAndClock(sumTravel, ScheduleTimeFormat.durationHmsUnsignedLong(sumTravel)));
        agg.setSumTransport(
                new TimeSecondsAndClock(sumTransport, ScheduleTimeFormat.durationHmsUnsignedLong(sumTransport)));
        agg.setSumIdle(new TimeSecondsAndClock(sumIdle, ScheduleTimeFormat.durationHmsSignedLong(sumIdle)));
        agg.setSumDuration(
                new TimeSecondsAndClock(sumDuration, ScheduleTimeFormat.durationHmsUnsignedLong(Math.max(0, sumDuration))));

        double meanActive = active > 0 ? (double) sumDurationActive / active : 0;
        agg.setMeanDurationActiveSeconds(meanActive);
        agg.setMeanDurationActiveClock(
                ScheduleTimeFormat.durationHms((int) Math.round(meanActive)));

        agg.setMaxDurationSeconds(maxDuration);
        agg.setMaxDurationClock(ScheduleTimeFormat.durationHms(maxDuration));

        if (minDurationActive == Integer.MAX_VALUE) {
            agg.setMinDurationActiveSeconds(-1);
            agg.setMinDurationActiveClock(null);
            agg.setDurationSpreadSeconds(0);
            agg.setDurationSpreadClock("00:00:00");
        } else {
            agg.setMinDurationActiveSeconds(minDurationActive);
            agg.setMinDurationActiveClock(ScheduleTimeFormat.durationHms(minDurationActive));
            int spread = maxDuration - minDurationActive;
            agg.setDurationSpreadSeconds(Math.max(0, spread));
            agg.setDurationSpreadClock(ScheduleTimeFormat.durationHms(Math.max(0, spread)));
        }

        agg.setActivePorterCount(active);
        agg.setIdlePorterCount(idle);

        if (sumDuration > 0) {
            agg.setFleetTravelShare(sumTravel / (double) sumDuration);
            agg.setFleetTransportShare(sumTransport / (double) sumDuration);
            agg.setFleetIdleShare(sumIdle / (double) sumDuration);
        } else {
            agg.setFleetTravelShare(0);
            agg.setFleetTransportShare(0);
            agg.setFleetIdleShare(0);
        }

        agg.setSchedulesAllChecksTrueCount(allChecks);
        agg.setScheduleCount(n);
        agg.setScheduleValidationPassRate(n > 0 ? allChecks / (double) n : 0);
        agg.setByPorter(rows);
        return agg;
    }

    private static PorterEffort buildPorterEffort(Solution solution) {
        List<PorterEffortRow> rows = new ArrayList<>();
        List<Double> shares = new ArrayList<>();
        for (FinalSchedule fs : solution.getFinalSchedules()) {
            int porterId = fs.getPorter() != null ? fs.getPorter().getId() : -1;
            List<Patient> pts = fs.getPatients();
            if (pts == null || pts.isEmpty()) {
                rows.add(new PorterEffortRow(porterId, 0, 0, 0));
                shares.add(0.0);
                continue;
            }
            int realCount = 0;
            int beds = 0;
            for (Patient p : pts) {
                if (p == null || p.isDummy()) {
                    continue;
                }
                realCount++;
                if (isHospitalBed(p)) {
                    beds++;
                }
            }
            double share = realCount > 0 ? beds / (double) realCount : 0;
            shares.add(share);

            List<Integer> streakMetrics = new ArrayList<>();
            int streak = 0;
            for (int i = 1; i < pts.size(); i++) {
                Patient cur = pts.get(i);
                if (cur.isDummy()) {
                    streak = 0;
                    continue;
                }
                if (isHospitalBed(cur)) {
                    streak++;
                    streakMetrics.add(streak);
                } else {
                    streak = 0;
                }
            }
            int maxStreak =
                    streakMetrics.stream().mapToInt(Integer::intValue).max().orElse(0);
            double medStreak = medianInt(streakMetrics);
            rows.add(new PorterEffortRow(porterId, share, maxStreak, medStreak));
        }
        double meanShare =
                shares.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double maxS = shares.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double minS =
                shares.isEmpty()
                        ? 0
                        : shares.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        return new PorterEffort(meanShare, maxS, minS, rows);
    }

    private static double medianInt(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        List<Integer> s = new ArrayList<>(values);
        s.sort(Integer::compareTo);
        int n = s.size();
        if (n % 2 == 1) {
            return s.get(n / 2);
        }
        return (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0;
    }

    private static boolean isHospitalBed(Patient p) {
        if (p.getMobilityAidPolicy() == null) {
            return false;
        }
        return TransportModeKind.isHospitalBed(p.getMobilityAidPolicy().getAidType());
    }

    /** C++: {@code end - processing - release_date} (here {@code processing} is transport duration). */
    private static double responseSeconds(Patient p) {
        int end = p.getEndTime().getSeconds();
        int proc = p.getTransportTime();
        int rel = p.getTime().getTimeAsked().getSeconds();
        return end - proc - rel;
    }

    /**
     * Porter wait between end of the previous service and when travel to the next request can start:
     * {@code max(0, max(timeAsked, prevEnd) - prevEnd)}, following route order and recomputing service end
     * with the graph (analogous to {@code removeIdle} without historical simulation clock). This avoids
     * using {@code start}/{@code end} from online merge, which are not always consecutive in the final
     * list.
     */
    private static double idleSecondsReplayedOnRoute(FinalSchedule fs, Graph graph) {
        List<Patient> pts = fs.getPatients();
        if (graph == null || pts == null || pts.size() < 2) {
            return 0;
        }
        Patient prev = pts.get(0);
        int prevEndSec = prev.getEndTime().getSeconds();
        if (prev.isDummy()) {
            prevEndSec = Math.max(prevEndSec, DomainConstants.SCHEDULE_START_TIME_SECONDS);
        }
        double sumIdle = 0;
        for (int i = 1; i < pts.size(); i++) {
            Patient cur = pts.get(i);
            if (cur.isDummy()) {
                continue;
            }
            int requestedSec = cur.getRequestedTime().getSeconds();
            int earliestSec = Math.max(requestedSec, prevEndSec);
            sumIdle += Math.max(0, earliestSec - prevEndSec);
            int travelSec =
                    graph.getTravelTimeBetweenTwoLocations(prev.getLocation(), cur.getLocation())
                            .getSeconds();
            int endSvc = earliestSec + travelSec + cur.getTransportTime();
            prevEndSec = endSvc;
            prev = cur;
        }
        return sumIdle;
    }

    private static List<Patient> collectPatients(Solution solution) {
        List<Patient> out = new ArrayList<>();
        for (FinalSchedule fs : solution.getFinalSchedules()) {
            if (fs.getPatients() == null) {
                continue;
            }
            for (Patient p : fs.getPatients()) {
                if (p != null && !p.isDummy()) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    private static String formatClock(TimeObject o) {
        return o == null ? "00:00:00" : o.toString();
    }

    private static String formatDurationSeconds(double seconds) {
        int s = (int) Math.round(seconds);
        return new TimeObject(Math.max(0, s)).toString();
    }

    private static final class Agg {
        int patientCount;
        double sumTardUnweighted;
        int tardyPatientCount;
        double sumResponseSec;
    }
}
