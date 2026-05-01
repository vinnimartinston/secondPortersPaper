package com.paper2.dto.metrics;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregated metrics (mirror of {@code print_resume_results} / {@code print_final_results} in
 * {@code printer.cpp}): what the Java domain can compute today; wheelchair/depot and CP-solver fields use
 * placeholders when not applicable.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentMetricsDto {

    private String instanceName;
    private Integer amountOfPorters;
    /** Real patients on final schedules (non-dummy). */
    private int transportedPatientCount;
    /** Wall-clock time of {@code simulator.start} for this instance, in seconds. */
    private double simulatorWallTimeSeconds;

    private Summary summary = new Summary();
    private Tardiness tardiness = new Tardiness();
    private Response response = new Response();
    private SetupIdle setupIdle = new SetupIdle();
    private PorterEffort porterEffort = new PorterEffort();
    private WheelchairDepot wheelchairDepot = new WheelchairDepot();
    /** Aggregates from {@link com.paper2.metrics.PorterScheduleRouteMetrics} (final routes). */
    private ScheduleTimeAggregates scheduleTimeAggregates = new ScheduleTimeAggregates();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        /** Maximum {@code endTime} among patients (seconds since midnight). */
        private int makespanSeconds;
        private String makespanClock;
        /** Σ max(0, end−due)·weight (aligned with {@link com.paper2.domain.Patient#calculateObjectiveValue()}). */
        private double sumWeightedTardiness;
        private double sumUnweightedTardinessSeconds;
        /** Value stored in {@link com.paper2.domain.Solution#getObjectiveValue()} (may be 0 if not updated). */
        private int objectiveValueFromSolution;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriorityBreakdown {
        private int priority;
        private int patientCount;
        /** Mean raw tardiness (s) → minutes per patient at this priority. */
        private double avgUnweightedTardinessMinutes;
        private int tardyPatientCount;
        /** {@code tardyPatientCount / patientCount}. */
        private double tardyPatientShare;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tardiness {
        private int countTardinessZero;
        private int countTardinessPositiveUpTo1800s;
        private int countTardinessAbove1800s;
        private List<PriorityBreakdown> byPriority = new ArrayList<>();
        /** Global mean raw tardiness (minutes per patient). */
        private double meanUnweightedTardinessMinutesAllPatients;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseByPriority {
        private int priority;
        private int patientCount;
        private double avgResponseMinutes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private List<ResponseByPriority> byPriority = new ArrayList<>();
        private double meanResponseMinutesAllPatients;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonPropertyOrder({
        "sumTravelSetupSeconds",
        "avgTravelSetupMinutesPerPatient",
        "sumIdleSeconds",
        "sumIdleClock",
        "avgIdleMinutesPerPorter",
        "idleTimeShareOfHorizon",
        "horizonSeconds",
        "idleNote"
    })
    public static class SetupIdle {
        private double sumTravelSetupSeconds;
        private double avgTravelSetupMinutesPerPatient;
        private double sumIdleSeconds;
        /** Same total as {@link #sumIdleSeconds}, duration as {@code HH:MM:SS}. */
        private String sumIdleClock;
        private double avgIdleMinutesPerPorter;
        /** Analogous to {@code Percentage_Idle_Time} in C++ (mean idle per porter / horizon in minutes). */
        private double idleTimeShareOfHorizon;
        private int horizonSeconds;
        /** E.g. missing {@code Graph}; or {@code null} when idle was computed by replaying the route. */
        private String idleNote;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PorterEffortRow {
        private int porterId;
        /** Share of “bed” requests ({@code Hospital Bed}) among real patients on the route (excludes dummy). */
        private double bedPatientShare;
        private int maxConsecutiveBeds;
        private double medianConsecutiveBedStreakMetric;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PorterEffort {
        /** Mean of “bed” fractions per porter ({@code GetEffortRate}). */
        private double effortRateMean;
        private double maxBedShareAcrossPorters;
        private double minBedShareAcrossPorters;
        private List<PorterEffortRow> byPorter = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSecondsAndClock {
        private long seconds;
        private String clock;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PorterScheduleTimeRow {
        private int porterId;
        private int travelSeconds;
        private String travelClock;
        private int transportSeconds;
        private String transportClock;
        private int idleSeconds;
        private String idleClock;
        private int durationSeconds;
        private String durationClock;
        private int realPatientCount;
        private boolean allValidationsTrue;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonPropertyOrder({
        "sumTravel",
        "sumTransport",
        "sumIdle",
        "sumDuration",
        "meanDurationActiveSeconds",
        "meanDurationActiveClock",
        "maxDurationSeconds",
        "maxDurationClock",
        "minDurationActiveSeconds",
        "minDurationActiveClock",
        "durationSpreadSeconds",
        "durationSpreadClock",
        "activePorterCount",
        "idlePorterCount",
        "fleetTravelShare",
        "fleetTransportShare",
        "fleetIdleShare",
        "schedulesAllChecksTrueCount",
        "scheduleCount",
        "scheduleValidationPassRate",
        "byPorter"
    })
    public static class ScheduleTimeAggregates {
        private TimeSecondsAndClock sumTravel = new TimeSecondsAndClock(0, "00:00:00");
        private TimeSecondsAndClock sumTransport = new TimeSecondsAndClock(0, "00:00:00");
        private TimeSecondsAndClock sumIdle = new TimeSecondsAndClock(0, "00:00:00");
        private TimeSecondsAndClock sumDuration = new TimeSecondsAndClock(0, "00:00:00");
        /** Mean duration over porters with {@code realPatientCount > 0} only (seconds; fraction allowed). */
        private double meanDurationActiveSeconds;
        private String meanDurationActiveClock;
        private int maxDurationSeconds;
        private String maxDurationClock;
        /** Minimum among active porters; {@code -1} / {@code null} if none active. */
        private int minDurationActiveSeconds;
        private String minDurationActiveClock;
        private int durationSpreadSeconds;
        private String durationSpreadClock;
        private int activePorterCount;
        private int idlePorterCount;
        /** {@code sumTravel / sumDuration} se {@code sumDuration > 0}. */
        private double fleetTravelShare;
        private double fleetTransportShare;
        private double fleetIdleShare;
        private int schedulesAllChecksTrueCount;
        private int scheduleCount;
        private double scheduleValidationPassRate;
        private List<PorterScheduleTimeRow> byPorter = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WheelchairDepot {
        private String note =
                "WC_indicator / depot timeline (printer.cpp ~682–866) not replicated in Java; numeric fields below are placeholders.";
        private int wcMaxChairsInUse;
        private double wcMaxUsageFraction;
        private List<Double> depotMinBalancePlaceholder = List.of();
        private double totalPickUp;
        private double totalDropOff;
        private double avgWalkingToDepotMinutes;
    }

}
