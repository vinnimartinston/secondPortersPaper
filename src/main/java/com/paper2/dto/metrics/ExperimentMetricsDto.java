package com.paper2.dto.metrics;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregated metrics (mirror of {@code print_resume_results} / {@code print_final_results} in
 * {@code printer.cpp}).
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
    @JsonPropertyOrder({"scheduleDurationSeconds", "scheduleDurationClock", "objectiveFunction"})
    public static class Summary {
        /** Elapsed seconds from schedule start (08:00) to last patient end; same as {@link SetupIdle#horizonSeconds}. */
        private int scheduleDurationSeconds;
        /** Same span as {@link #scheduleDurationSeconds}, as duration {@code HH:MM:SS}. */
        private String scheduleDurationClock;
        private SummaryObjectiveFunction objectiveFunction = new SummaryObjectiveFunction();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonPropertyOrder({"value", "tardinessTerm", "depotPenaltyTerm"})
    public static class SummaryObjectiveFunction {
        /** {@code tardinessTerm.value + depotPenaltyTerm.value} on the committed final plan (clamped to {@code int}). */
        private int value;
        private TardinessTerm tardinessTerm = new TardinessTerm();
        private DepotPenaltyTerm depotPenaltyTerm = new DepotPenaltyTerm();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonPropertyOrder({"value", "sumUnweightedTardinessSeconds", "byPriority"})
    public static class TardinessTerm {
        /** Σ (lateness seconds × priority weight) on final schedules. */
        private double value;
        /** Σ raw lateness seconds on final schedules. */
        private double sumUnweightedTardinessSeconds;
        private List<TardinessTermByPriority> byPriority = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonPropertyOrder({"priority", "value", "coefficient", "sumUnweightedTardinessSeconds"})
    public static class TardinessTermByPriority {
        private int priority;
        /** {@code sumUnweightedTardinessSeconds × coefficient}. */
        private double value;
        /** Priority weight ({@code penalty weight} from input). */
        private int coefficient;
        private double sumUnweightedTardinessSeconds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonPropertyOrder({"value", "coefficient", "totalWheelchairViolationSecondsBelowZero"})
    public static class DepotPenaltyTerm {
        /** {@code totalWheelchairViolationSecondsBelowZero × coefficient}. */
        private long value;
        /** Soft-penalty coefficient from the experiment JSON ({@code penalty}). */
        private int coefficient;
        /** Seconds with negative wheelchair stock summed across depots (evaluation horizon). */
        private long totalWheelchairViolationSecondsBelowZero;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriorityBreakdown {
        private int priority;
        private int patientCount;
        /** Σ raw tardiness (s) at this priority, in minutes (check: sum over priorities × 60 ≈ {@link TardinessTerm#sumUnweightedTardinessSeconds}). */
        private double sumUnweightedTardinessMinutes;
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
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WheelchairDepot {
        /** Peak concurrent wheelchairs out of depots ({@code WC_max} in legacy {@code printer.cpp}). */
        private int maxWheelchairsUsedAtSameTime;
        /** Share of horizon seconds at that peak ({@code WC_max_usage} in legacy {@code printer.cpp}). */
        private double timeShareUsingMaxWheelchairs;
        /** Per depot (sorted by id): {@code -min(depotBalanceTracker)} over the horizon. */
        private List<Double> depotMinBalance = List.of();
        /** Total depot pick-up events across depots. */
        private double totalPickUp;
        /** Total depot drop-off events across depots. */
        private double totalDropOff;
        /** Mean walk/setup time to depot per pick-up or drop-off visit, in minutes. */
        private double avgWalkingToDepotMinutes;
    }

}
