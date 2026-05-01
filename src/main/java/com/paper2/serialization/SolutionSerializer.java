    package com.paper2.serialization;

    import java.io.IOException;
    import java.io.UncheckedIOException;
    import java.nio.file.Files;
    import java.nio.file.Path;
    import java.util.ArrayList;
    import java.util.HashMap;
    import java.util.HashSet;
    import java.util.List;
    import java.util.Map;
    import java.util.Set;
    import java.util.TreeMap;

    import com.fasterxml.jackson.core.JsonProcessingException;
    import com.fasterxml.jackson.databind.ObjectMapper;
    import com.fasterxml.jackson.databind.SerializationFeature;
    import com.paper2.domain.Depot;
    import com.paper2.domain.DomainConstants;
    import com.paper2.domain.FinalSchedule;
    import com.paper2.domain.InstanceMetadata;
    import com.paper2.domain.Patient;
    import com.paper2.domain.Solution;
    import com.paper2.domain.TimeObject;
    import com.paper2.domain.inventory.DepotBalanceTimeline;
    import com.paper2.domain.inventory.SolutionInventoryState;
    import com.paper2.domain.TimeProperties;
    import com.paper2.domain.TransportModeKind;
    import com.paper2.dto.LocationDto;
    import com.paper2.dto.PriorityDto;
    import com.paper2.dto.TransportModeDto;
    import com.paper2.dto.InstanceMetadataDto;
    import com.paper2.dto.solution.DepotInventoryTimelineSummaryDto;
    import com.paper2.dto.solution.DepotSolutionSnapshotDto;
    import com.paper2.dto.solution.FinalScheduleSnapshotDto;
    import com.paper2.dto.solution.PatientSolutionSnapshotDto;
    import com.paper2.dto.solution.PatientSolutionTimesDto;
    import com.paper2.dto.solution.PorterScheduleSummaryDto;
    import com.paper2.dto.solution.SchedulePatientSummaryDto;
    import com.paper2.dto.solution.ScheduleTimeSummaryDto;
    import com.paper2.dto.solution.SolutionDepotInventoryDto;
    import com.paper2.dto.solution.SolutionObjectiveFunctionDto;
    import com.paper2.dto.solution.SolutionResultDto;
    import com.paper2.metrics.DepotInventoryHorizonSummarizer;
    import com.paper2.metrics.PorterScheduleRouteMetrics;
    import com.paper2.metrics.ScheduleTimeFormat;
    import com.paper2.metrics.WheelchairDepotViolationSecondsCalculator;
    import com.paper2.simulator.solver.localsearch.TotalUnweightedTardinessObjective;

    /**
     * Converts {@link Solution} to {@link SolutionResultDto} / JSON for {@code *_solution.json} files:
     * one entry per porter with time summary, per-priority counts, and an ordered list of real patients
     * (dummy excluded).
     */
public final class SolutionSerializer {

    private static final String CLOCK_ZERO = "00:00:00";

    private static final ObjectMapper MAPPER = createMapper();

        private record RouteBreakdown(
                int transportedPatientCount, int hospitalBedCount, TreeMap<Integer, Integer> patientsByPriority) {}

        private SolutionSerializer() {}

        private static ObjectMapper createMapper() {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            return mapper;
        }

        /**
         * Shared {@link ObjectMapper} (indented output) used by {@link #toJson(Solution)} and
         * {@link #write(Path, Solution)}.
         *
         * @return mapper configured for readable serialization
         */
        public static ObjectMapper mapper() {
            return MAPPER;
        }

        /**
         * Builds the root DTO with simulation clock, objective function snapshot, final schedules, then depot inventory.
         *
         * @param solution final simulation state; must not be {@code null}
         * @return DTO ready for Jackson
         * @throws IllegalStateException if non-dummy patients are missing from {@link FinalSchedule}s, duplicated across
         *     porters, or scheduled ids are not exactly {@link Solution#getPatients()} (excluding dummy)
         */
        public static SolutionResultDto toDto(Solution solution) {
            assertExportPatientPartition(solution);
            List<FinalScheduleSnapshotDto> finalSnapshots = finalSchedulesToDtos(solution);
            String clockStr = formatClock(solution.getSimulatorClock());
            DepotInventorySerialization s = computeDepotInventorySerialization(solution);
            SolutionObjectiveFunctionDto objectiveFunction =
                    new SolutionObjectiveFunctionDto(
                            solution.getObjectiveValue(),
                            s.evaluationWindowStartSeconds(),
                            s.evaluationWindowStartClock(),
                            s.evaluationWindowEndExclusiveSeconds(),
                            s.evaluationHorizonLastIncludedSecondClock(),
                            s.depotInventoryViolationPenaltyCoefficient(),
                            s.totalWheelchairViolationSecondsBelowZero(),
                            s.depotPenaltyTerm(),
                            s.unweightedTardinessSumSeconds());
            SolutionDepotInventoryDto depotInventory = new SolutionDepotInventoryDto(s.depotRows());
            InstanceMetadataDto metadata = toMetadataDto(solution.getMetadata());
            return new SolutionResultDto(metadata, clockStr, objectiveFunction, finalSnapshots, depotInventory);
        }

        private static InstanceMetadataDto toMetadataDto(InstanceMetadata metadata) {
            if (metadata == null) {
                return null;
            }
            return new InstanceMetadataDto(
                    metadata.getAmountOfPatients(),
                    metadata.getProfile(),
                    metadata.getAmountOfDepots(),
                    metadata.getRoundTripsPercentage(),
                    metadata.getWheelchairChangesPercentage(),
                    metadata.getInstanceName());
        }

        private record DepotInventorySerialization(
                int evaluationWindowStartSeconds,
                String evaluationWindowStartClock,
                int evaluationWindowEndExclusiveSeconds,
                String evaluationHorizonLastIncludedSecondClock,
                int depotInventoryViolationPenaltyCoefficient,
                long totalWheelchairViolationSecondsBelowZero,
                long depotPenaltyTerm,
                long unweightedTardinessSumSeconds,
                List<DepotSolutionSnapshotDto> depotRows) {}

        /**
         * Shared computation for objective-function scalars and per-depot inventory rows.
         */
        private static DepotInventorySerialization computeDepotInventorySerialization(Solution solution) {
            List<Depot> domainDepots = solution.getDepots() != null ? solution.getDepots() : List.of();
            int shiftStartSeconds = DomainConstants.SCHEDULE_START_TIME_SECONDS;
            String shiftStartClock = formatClock(new TimeObject(shiftStartSeconds));
            int windowEndExclusiveSeconds =
                    WheelchairDepotViolationSecondsCalculator.evaluationWindowEndExclusiveSeconds(solution);
            String lastIncludedClock;
            if (windowEndExclusiveSeconds > shiftStartSeconds) {
                lastIncludedClock = formatClock(new TimeObject(windowEndExclusiveSeconds - 1));
            } else {
                lastIncludedClock = shiftStartClock;
            }

            Map<Integer, Long> violationSecondsByDepotId =
                    domainDepots.isEmpty()
                            ? Map.of()
                            : WheelchairDepotViolationSecondsCalculator.violationSecondsPerDepot(solution);
            long totalViolationSeconds = 0;
            for (long segmentSeconds : violationSecondsByDepotId.values()) {
                totalViolationSeconds += segmentSeconds;
            }

            int penaltyCoefficient = solution.getDepotInventoryViolationPenaltyCoefficient();
            long depotPenaltyTerm = totalViolationSeconds * penaltyCoefficient;
            long unweightedTardinessSumSeconds = TotalUnweightedTardinessObjective.evaluate(solution);

            SolutionInventoryState inventoryState = solution.getInventoryState();
            List<DepotSolutionSnapshotDto> depotRows = new ArrayList<>();
            for (Depot depot : domainDepots) {
                var location = depot.getLocation();
                LocationDto locationDto = new LocationDto(location.getOrigin(), location.getDestination());
                long depotViolationSeconds = violationSecondsByDepotId.getOrDefault(depot.getId(), 0L);
                DepotInventoryTimelineSummaryDto finalSummary = null;
                if (inventoryState != null) {
                    DepotBalanceTimeline fin = inventoryState.getFinalCommittedByDepotId().get(depot.getId());
                    if (fin != null && windowEndExclusiveSeconds > shiftStartSeconds) {
                        finalSummary =
                                DepotInventoryHorizonSummarizer.summarizeFinalTimeline(
                                        fin, shiftStartSeconds, windowEndExclusiveSeconds);
                    }
                }
                depotRows.add(
                        new DepotSolutionSnapshotDto(
                                depot.getId(),
                                locationDto,
                                depot.getInitialWheelchairInventory(),
                                depotViolationSeconds,
                                finalSummary));
            }

            return new DepotInventorySerialization(
                    shiftStartSeconds,
                    shiftStartClock,
                    windowEndExclusiveSeconds,
                    lastIncludedClock,
                    penaltyCoefficient,
                    totalViolationSeconds,
                    depotPenaltyTerm,
                    unweightedTardinessSumSeconds,
                    depotRows);
        }

        /**
         * Serializes the solution to a single JSON string.
         *
         * @param solution solution to export
         * @return indented JSON
         * @throws UncheckedIOException if Jackson serialization fails
         */
        public static String toJson(Solution solution) {
            try {
                return MAPPER.writeValueAsString(toDto(solution));
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
        }

        /**
         * Writes {@code solution} to disk, creating parent directories if needed.
         *
         * @param path destination file (e.g. {@code .../stem_solution.json})
         * @param solution solution to persist
         * @throws IOException on I/O failure
         */
        public static void write(Path path, Solution solution) throws IOException {
            Files.createDirectories(path.getParent() != null ? path.getParent() : Path.of("."));
            MAPPER.writeValue(path.toFile(), toDto(solution));
        }

        /**
         * Ensures each real patient id from {@link Solution#getPatients()} appears exactly once across
         * {@link Solution#getFinalSchedules()} (dummy entries ignored).
         */
        private static void assertExportPatientPartition(Solution solution) {
            Set<Integer> expected = new HashSet<>();
            if (solution.getPatients() != null) {
                for (Patient p : solution.getPatients()) {
                    if (p != null && !p.isDummy()) {
                        expected.add(p.getId());
                    }
                }
            }
            Map<Integer, Integer> countById = new HashMap<>();
            if (solution.getFinalSchedules() != null) {
                for (FinalSchedule fs : solution.getFinalSchedules()) {
                    if (fs == null || fs.getPatients() == null) {
                        continue;
                    }
                    for (Patient p : fs.getPatients()) {
                        if (p == null || p.isDummy()) {
                            continue;
                        }
                        countById.merge(p.getId(), 1, Integer::sum);
                    }
                }
            }
            List<String> problems = new ArrayList<>();
            for (Integer id : expected) {
                int c = countById.getOrDefault(id, 0);
                if (c == 0) {
                    problems.add(patientRef(id) + " is not in any final schedule");
                } else if (c > 1) {
                    problems.add(patientRef(id) + " appears " + c + " times across final schedules");
                }
            }
            for (Integer id : countById.keySet()) {
                if (!expected.contains(id)) {
                    problems.add(patientRef(id) + " is in a final schedule but not in solution.patients");
                }
            }
            if (!problems.isEmpty()) {
                throw new IllegalStateException("Solution JSON export: " + String.join("; ", problems));
            }
        }

        private static String patientRef(int id) {
            return "patient " + id;
        }

        private static List<FinalScheduleSnapshotDto> finalSchedulesToDtos(Solution solution) {
            List<FinalScheduleSnapshotDto> out = new ArrayList<>();
            if (solution.getFinalSchedules() == null) {
                return out;
            }
            for (FinalSchedule fs : solution.getFinalSchedules()) {
                int porterId = fs.getPorter() != null ? fs.getPorter().getId() : -1;
                out.add(toFinalScheduleSnapshotDto(solution, fs, porterId));
            }
            return out;
        }

        private static FinalScheduleSnapshotDto toFinalScheduleSnapshotDto(
                Solution solution, FinalSchedule fs, int porterId) {
            List<Patient> pts = fs.getPatients();
            Patient dummy = pts != null && !pts.isEmpty() ? pts.get(0) : null;

            PorterScheduleRouteMetrics routeMetrics = PorterScheduleRouteMetrics.compute(solution, fs, porterId);
            RouteBreakdown breakdown = routeBreakdown(pts);

            int routeStartSec = summaryStartSeconds(dummy);
            int lastClockSec = solution.getSimulatorClock().getSeconds();
            long totalWorkedSec = Math.max(0, (long) lastClockSec - routeStartSec);
            PorterScheduleSummaryDto summary =
                    new PorterScheduleSummaryDto(
                            new SchedulePatientSummaryDto(
                                    breakdown.transportedPatientCount(),
                                    breakdown.hospitalBedCount(),
                                    breakdown.patientsByPriority()),
                            new ScheduleTimeSummaryDto(
                                    summaryStartClock(dummy),
                                    scheduleWorkingEndClock(solution, porterId),
                                    ScheduleTimeFormat.durationHms(Math.max(0, routeMetrics.durationSeconds())),
                                    ScheduleTimeFormat.durationHms(routeMetrics.sumTransportSeconds()),
                                    ScheduleTimeFormat.durationHms(routeMetrics.sumTravelSeconds()),
                                    ScheduleTimeFormat.durationHmsSigned(routeMetrics.idleSeconds()),
                                    totalWorkedSec,
                                    ScheduleTimeFormat.durationHms(
                                            (int) Math.min(totalWorkedSec, Integer.MAX_VALUE))));

            List<PatientSolutionSnapshotDto> patients = new ArrayList<>();
            int sequence = 1;
            if (pts != null) {
                for (Patient p : pts) {
                    if (p == null || p.isDummy()) {
                        continue;
                    }
                    patients.add(toPatientSolutionSnapshot(p, sequence++));
                }
            }

            return new FinalScheduleSnapshotDto(porterId, summary, patients);
        }

        /** Counts real patients, hospital beds ({@link TransportModeKind#isHospitalBed}), and by priority. */
        private static RouteBreakdown routeBreakdown(List<Patient> pts) {
            TreeMap<Integer, Integer> byPriority = new TreeMap<>();
            int transported = 0;
            int beds = 0;
            if (pts != null) {
                for (Patient p : pts) {
                    if (p == null || p.isDummy()) {
                        continue;
                    }
                    transported++;
                    int pv = p.getPriority() != null ? p.getPriority().getPriority() : 0;
                    byPriority.merge(pv, 1, Integer::sum);
                    var mobility = p.getMobilityAidPolicy();
                    if (mobility != null
                            && mobility.getAidType() != null
                            && TransportModeKind.isHospitalBed(mobility.getAidType())) {
                        beds++;
                    }
                }
            }
            return new RouteBreakdown(transported, beds, byPriority);
        }

        /** Route start in summary: {@code dummy.start} or default shift start. */
        private static String summaryStartClock(Patient dummy) {
            return formatClock(new TimeObject(summaryStartSeconds(dummy)));
        }

        /** Seconds since midnight for route start (same basis as {@link #summaryStartClock}). */
        private static int summaryStartSeconds(Patient dummy) {
            if (dummy != null && dummy.isDummy()) {
                TimeProperties t = dummy.getTime();
                if (t.getStartTime() != null && t.getStartTime().getSeconds() > 0) {
                    return t.getStartTime().getSeconds();
                }
            }
            return DomainConstants.SCHEDULE_START_TIME_SECONDS;
        }

        /** Porter work-shift end per {@link Solution#getSchedules()}. */
        private static String scheduleWorkingEndClock(Solution solution, int porterId) {
            if (solution.getSchedules() == null
                    || porterId < 0
                    || porterId >= solution.getSchedules().size()) {
            return CLOCK_ZERO;
        }
        var end = solution.getSchedules().get(porterId).getTime().getEndTime();
        return formatClock(end);
    }

        private static PatientSolutionSnapshotDto toPatientSolutionSnapshot(Patient p, int sequence) {
            TimeProperties t = p.getTime();
            var loc = p.getLocation();
            LocationDto locationDto = new LocationDto(loc.getOrigin(), loc.getDestination());
            var pr = p.getPriority();
            PriorityDto priorityDto = new PriorityDto(pr.getPriority(), pr.getWeight());
            PatientSolutionTimesDto timeDto =
                    new PatientSolutionTimesDto(
                            formatClock(t.getTimeAsked()),
                            formatClock(t.getTravelTime()),
                            formatClock(t.getStartTime()),
                            formatClock(t.getTransportTime()),
                            formatClock(t.getEndTime()),
                            formatClock(t.getDueDate()),
                            formatClock(t.getLateness()),
                            formatResponseTime(t.getStartTime(), t.getTimeAsked()));
            var mobility = p.getMobilityAidPolicy();
            TransportModeDto transportMode =
                    mobility == null
                            ? new TransportModeDto("", false, false)
                            : new TransportModeDto(
                                    mobility.getAidType(),
                                    mobility.isRetainEquipmentAtDestination(),
                                    mobility.isEquipmentPresentAtOrigin());
            return new PatientSolutionSnapshotDto(
                    p.getId(), sequence, timeDto, transportMode, priorityDto, locationDto);
        }

    private static String formatClock(TimeObject o) {
        return o == null ? CLOCK_ZERO : o.toString();
    }

    /** {@code start - timeAsked} as duration {@code HH:MM:SS}. */
    private static String formatResponseTime(TimeObject start, TimeObject timeAsked) {
        if (start == null || timeAsked == null) {
            return CLOCK_ZERO;
        }
            return start.subtractTime(timeAsked).toString();
        }
    }
