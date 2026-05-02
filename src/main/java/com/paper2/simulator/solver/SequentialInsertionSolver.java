package com.paper2.simulator.solver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.paper2.domain.Depot;
import com.paper2.domain.Patient;
import com.paper2.domain.RouteTiming;
import com.paper2.domain.Schedule;
import com.paper2.domain.Solution;
import com.paper2.domain.TimeObject;
import com.paper2.metrics.inventory.DepotSelectionByObjective;
import com.paper2.simulator.Solver;
import com.paper2.simulator.solver.localsearch.LocalSearchScheduleSupport;
import com.paper2.simulator.solver.localsearch.TotalUnweightedTardinessObjective;

/**
 * Core simulation orchestration.
 * <p>
 * Suggested location: {@code com.paper2.simulator} — domain of the simulator, alongside
 * {@code regras}, {@code solver}, etc.
 */
public class SequentialInsertionSolver implements Solver {

    @Override
    public Solution solve(Solution solution) {

        List<Patient> sortedPatients = this.sortPatientsByPriority(solution.getPatientsToSchedule());

        for (Patient patient : sortedPatients) {
            int bestScheduleId = this.findBestScheduleId(patient, solution);
            Schedule schedule = solution.getSchedules().get(bestScheduleId);
            Depot depotForInsert =
                    resolveDepotForTailInsertionLeg(solution, schedule, bestScheduleId, patient);
            schedule.insertPatient(
                    patient,
                    solution.getGraph(),
                    solution.getDepots(),
                    schedule.getPorter(),
                    depotForInsert);
            solution.removeIdle();
            solution.rebuildWorkingInventory();
            TotalUnweightedTardinessObjective.syncSolutionField(solution);
        }

        return solution;
    }

    private List<Patient> sortPatientsByPriority(List<Patient> patients) {
        return patients.stream()
                .sorted(
                        Comparator.comparingInt(Patient::getPriorityValue)
                                .reversed()
                                .thenComparingInt(Patient::getDueDate))
                .toList();
    }

    /**
     * Chooses the schedule that minimizes the combined objective by evaluating tail insertions on deep-copied
     * chains only — working schedules and {@link com.paper2.domain.inventory.SolutionInventoryState} are not mutated.
     */
    private int findBestScheduleId(Patient patientToInsert, Solution solution) {
        long bestCombinedObjective = Long.MAX_VALUE;
        int bestScheduleId = -1;
        List<Schedule> schedules = solution.getSchedules();
        for (int scheduleIndex = 0; scheduleIndex < schedules.size(); scheduleIndex++) {
            Schedule schedule = schedules.get(scheduleIndex);
            List<Patient> nodes = LocalSearchScheduleSupport.orderedNodes(schedule);
            List<Patient> trial = new ArrayList<>(nodes.size() + 1);
            for (Patient p : nodes) {
                trial.add(new Patient(p));
            }
            trial.add(new Patient(patientToInsert));
            LocalSearchScheduleSupport.relinkSequential(trial);
            long combinedObjective =
                    LocalSearchScheduleSupport.evaluateCombinedTrialChain(solution, scheduleIndex, trial);
            if (combinedObjective < bestCombinedObjective) {
                bestCombinedObjective = combinedObjective;
                bestScheduleId = schedule.getId();
            }
        }
        return bestScheduleId;
    }

    /**
     * Depot chosen by {@link DepotSelectionByObjective} for the new tail leg (after direct-time refresh on a trial
     * chain), so {@link Schedule#insertPatient} uses the same {@link com.paper2.domain.Graph} detour as the objective.
     */
    private static Depot resolveDepotForTailInsertionLeg(
            Solution solution, Schedule schedule, int scheduleId, Patient patientToInsert) {
        if (solution.getGraph() == null
                || solution.getDepots() == null
                || solution.getDepots().isEmpty()
                || schedule.getPorter() == null) {
            return null;
        }
        List<Patient> nodes = LocalSearchScheduleSupport.orderedNodes(schedule);
        List<Patient> trial = new ArrayList<>(nodes.size() + 1);
        for (Patient p : nodes) {
            trial.add(new Patient(p));
        }
        trial.add(new Patient(patientToInsert));
        LocalSearchScheduleSupport.relinkSequential(trial);
        Patient dummyHead = trial.get(0);
        for (Patient cur = dummyHead.getNext(); cur != null; cur = cur.getNext()) {
            Patient prev = cur.getPrevious();
            TimeObject earliest =
                    RouteTiming.earliestStart(
                            cur.getRequestedTime(), prev.getEndTime(), solution.getSimulatorClock());
            TimeObject directTravel =
                    solution.getGraph().getTravelTimeBetweenTwoLocations(prev.getLocation(), cur.getLocation());
            cur.updateTimeFromLastPatient(earliest, directTravel);
        }
        List<List<Patient>> overrides =
                DepotSelectionByObjective.emptyChainOverrides(solution.getSchedules().size());
        overrides.set(scheduleId, trial);
        DepotSelectionByObjective.DepotLegPlan plan = DepotSelectionByObjective.buildPlan(solution, overrides);
        Patient predecessor = trial.get(trial.size() - 2);
        int legIndex = trial.size() - 1;
        return DepotSelectionByObjective.depotForLeg(
                plan, scheduleId, legIndex, predecessor, solution.getDepots(), solution.getGraph());
    }
}