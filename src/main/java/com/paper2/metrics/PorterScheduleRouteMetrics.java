package com.paper2.metrics;

import java.util.List;

import com.paper2.domain.DomainConstants;
import com.paper2.domain.FinalSchedule;
import com.paper2.domain.Patient;
import com.paper2.domain.Solution;
import com.paper2.domain.TimeObject;

/**
 * Per-route timings for one porter’s final schedule (same logic as {@code scheduleSummary.time} in the
 * solution JSON): travel/transport sums, duration since {@link DomainConstants#SCHEDULE_START_TIME_SECONDS},
 * derived idle, and cross-check flags against shift end in {@link Solution#getSchedules()}.
 * <p>
 * Components: {@code porterId}; {@code sumTravelSeconds} / {@code sumTransportSeconds}; {@code durationSeconds}
 * = {@code lastEnd - scheduleStart}; {@code idleSeconds} = duration − travel − transport;
 * {@code effectiveDummyEndSeconds} (dummy end); {@code lastEndSeconds}; {@code realPatientCount};
 * three consistency booleans (end vs shift, span, travel+transport+idle sum).
 */
public record PorterScheduleRouteMetrics(
        int porterId,
        int sumTravelSeconds,
        int sumTransportSeconds,
        int durationSeconds,
        int idleSeconds,
        int effectiveDummyEndSeconds,
        int lastEndSeconds,
        int realPatientCount,
        boolean lastPatientEndMatchesScheduleEnd,
        boolean lastEndMinusDummyEndEqualsDuration,
        boolean travelTransportIdleSumEqualsDuration) {

    /**
     * Computes route metrics from the final schedule and the porter’s working calendar.
     *
     * @param solution global solution (working and final schedules)
     * @param fs final schedule for the porter (first node is dummy)
     * @param porterId index / id of the porter (to read {@link Solution#getSchedules()})
     * @return aggregated values and validation flags
     */
    public static PorterScheduleRouteMetrics compute(Solution solution, FinalSchedule fs, int porterId) {
        List<Patient> pts = fs.getPatients();
        Patient dummy = pts != null && !pts.isEmpty() ? pts.get(0) : null;

        int sumTravelSec = 0;
        int sumTransportSec = 0;
        Patient lastReal = null;
        if (pts != null) {
            for (Patient p : pts) {
                if (p == null || p.isDummy()) {
                    continue;
                }
                TimeObject tr = p.getTravelTime();
                sumTravelSec += tr != null ? tr.getSeconds() : 0;
                sumTransportSec += p.getTransportTime();
                lastReal = p;
            }
        }

        int scheduleStartSec = DomainConstants.SCHEDULE_START_TIME_SECONDS;
        int rawDummyEnd =
                dummy != null && dummy.getEndTime() != null
                        ? dummy.getEndTime().getSeconds()
                        : scheduleStartSec;
        int dummyEndSec = rawDummyEnd > 0 ? rawDummyEnd : scheduleStartSec;
        int lastEndSec =
                lastReal != null && lastReal.getEndTime() != null
                        ? lastReal.getEndTime().getSeconds()
                        : dummyEndSec;

        int durationSec = lastEndSec - scheduleStartSec;
        int idleSec = durationSec - sumTravelSec - sumTransportSec;

        TimeObject workingEnd = workingScheduleEnd(solution, porterId);
        boolean lastMatchesSchedule =
                workingEnd != null
                        && lastReal != null
                        && lastReal.getEndTime() != null
                        && lastReal.getEndTime().getSeconds() == workingEnd.getSeconds();
        boolean spanEqualsDuration = (lastEndSec - dummyEndSec) == durationSec;
        boolean sumBalanced = sumTravelSec + sumTransportSec + idleSec == durationSec;

        int realCount = 0;
        if (pts != null) {
            for (Patient p : pts) {
                if (p != null && !p.isDummy()) {
                    realCount++;
                }
            }
        }

        return new PorterScheduleRouteMetrics(
                porterId,
                sumTravelSec,
                sumTransportSec,
                durationSec,
                idleSec,
                dummyEndSec,
                lastEndSec,
                realCount,
                lastMatchesSchedule,
                spanEqualsDuration,
                sumBalanced);
    }

    /**
     * End of the porter’s work shift in {@code solution.getSchedules()}, or {@code null} if the index is
     * invalid.
     */
    private static TimeObject workingScheduleEnd(Solution solution, int porterId) {
        if (solution.getSchedules() == null
                || porterId < 0
                || porterId >= solution.getSchedules().size()) {
            return null;
        }
        return solution.getSchedules().get(porterId).getTime().getEndTime();
    }
}
