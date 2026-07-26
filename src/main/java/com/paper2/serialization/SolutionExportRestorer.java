package com.paper2.serialization;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.paper2.domain.DomainConstants;
import com.paper2.domain.FinalSchedule;
import com.paper2.domain.Input;
import com.paper2.domain.Patient;
import com.paper2.domain.Schedule;
import com.paper2.domain.Solution;
import com.paper2.domain.TimeObject;
import com.paper2.dto.solution.FinalScheduleSnapshotDto;
import com.paper2.dto.solution.PatientSolutionSnapshotDto;
import com.paper2.dto.solution.PatientSolutionTimesDto;
import com.paper2.dto.solution.SolutionObjectiveFunctionDto;
import com.paper2.dto.solution.SolutionResultDto;
import com.paper2.mapper.InputMapper;
import com.paper2.metrics.ScheduleTimeFormat;

/**
 * Rebuilds a {@link Solution} from instance input plus an exported {@link SolutionResultDto}, so metrics and
 * other post-processing can run without re-simulating.
 */
public final class SolutionExportRestorer {

    private SolutionExportRestorer() {}

    /**
     * @param input problem instance (graph, depots, static patient data)
     * @param exported committed final plan from {@code *_solution.json}
     * @param depotInventoryViolationPenaltyCoefficient penalty coefficient for depot OF term
     * @return solution with {@link Solution#getFinalSchedules()} populated from export
     */
    public static Solution restore(
            Input input, SolutionResultDto exported, int depotInventoryViolationPenaltyCoefficient) {
        if (input == null) {
            throw new IllegalArgumentException("input is null");
        }
        if (exported == null) {
            throw new IllegalArgumentException("exported solution is null");
        }

        Solution solution = new Solution(input);
        solution.setDepotInventoryViolationPenaltyCoefficient(depotInventoryViolationPenaltyCoefficient);
        if (exported.getMetadata() != null) {
            solution.setMetadata(InputMapper.toMetadata(exported.getMetadata()));
        }
        if (exported.getSimulatorClock() != null) {
            solution.setSimulatorClock(new TimeObject(ScheduleTimeFormat.parseHms(exported.getSimulatorClock())));
        }
        SolutionObjectiveFunctionDto objective = exported.getObjectiveFunction();
        if (objective != null) {
            solution.setObjectiveValue(objective.getObjectiveValue());
        }

        Set<Integer> transported = new LinkedHashSet<>();
        List<FinalScheduleSnapshotDto> scheduleRows = exported.getFinalSchedules();
        if (scheduleRows != null) {
            for (FinalScheduleSnapshotDto row : scheduleRows) {
                if (row == null) {
                    continue;
                }
                restoreFinalSchedule(input, solution, row, transported);
            }
        }
        solution.setTransportedPatients(transported);
        mirrorFinalSchedulesToWorkingSchedules(solution);
        return solution;
    }

    private static void restoreFinalSchedule(
            Input input, Solution solution, FinalScheduleSnapshotDto row, Set<Integer> transported) {
        int porterId = row.getPorterId();
        if (solution.getFinalSchedules() == null
                || porterId < 0
                || porterId >= solution.getFinalSchedules().size()) {
            throw new IllegalArgumentException("Invalid porterId in exported final schedule: " + porterId);
        }

        FinalSchedule finalSchedule = solution.getFinalSchedules().get(porterId);
        finalSchedule.getPatients().clear();
        Patient dummy = new Patient(input.getDummyPatient());
        dummy.getEndTime().setSeconds(DomainConstants.SCHEDULE_START_TIME_SECONDS);
        finalSchedule.getPatients().add(dummy);

        List<PatientSolutionSnapshotDto> patients = row.getPatients();
        if (patients != null) {
            for (PatientSolutionSnapshotDto snap : patients) {
                if (snap == null) {
                    continue;
                }
                Patient inputRow = input.getPatientById(snap.getId());
                if (inputRow == null) {
                    throw new IllegalArgumentException(
                            "Patient id " + snap.getId() + " missing from input instance");
                }
                Patient outputRow = patientWithExportedTimes(new Patient(inputRow), snap);
                finalSchedule.getPatients().add(Patient.snapshotFromInputAndOutput(inputRow, outputRow));
                transported.add(snap.getId());
            }
        }
    }

    private static Patient patientWithExportedTimes(Patient template, PatientSolutionSnapshotDto snap) {
        Patient output = template;
        PatientSolutionTimesDto times = snap.getTime();
        if (times != null) {
            applyExportedTimes(output, times);
        }
        output.setDepotIdVisitedBeforeNext(snap.getDepotIdVisitedBeforeNext());
        return output;
    }

    private static void applyExportedTimes(Patient patient, PatientSolutionTimesDto times) {
        var t = patient.getTime();
        t.getTimeAsked().setSeconds(ScheduleTimeFormat.parseHms(times.getTimeAsked()));
        t.getDueDate().setSeconds(ScheduleTimeFormat.parseHms(times.getDueDate()));
        t.getTravelTime().setSeconds(ScheduleTimeFormat.parseHms(times.getTravelTime()));
        t.getStartTime().setSeconds(ScheduleTimeFormat.parseHms(times.getStart()));
        t.getTransportTime().setSeconds(ScheduleTimeFormat.parseHms(times.getTransportTime()));
        t.getEndTime().setSeconds(ScheduleTimeFormat.parseHms(times.getEnd()));
        t.getLateness().setSeconds(ScheduleTimeFormat.parseHms(times.getLateness()));
    }

    private static void mirrorFinalSchedulesToWorkingSchedules(Solution solution) {
        if (solution.getSchedules() == null || solution.getFinalSchedules() == null) {
            return;
        }
        int n = Math.min(solution.getSchedules().size(), solution.getFinalSchedules().size());
        for (int porterId = 0; porterId < n; porterId++) {
            Schedule working = solution.getSchedules().get(porterId);
            FinalSchedule finalSchedule = solution.getFinalSchedules().get(porterId);
            working.clearScheduledPatients();
            List<Patient> pts = finalSchedule.getPatients();
            if (pts == null || pts.size() <= 1) {
                continue;
            }
            for (int i = 1; i < pts.size(); i++) {
                working.appendPatientPreserveTimes(new Patient(pts.get(i)));
            }
        }
    }
}
