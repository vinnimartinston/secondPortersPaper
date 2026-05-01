package com.paper2.simulator.policies;

import com.paper2.domain.FinalSchedule;
import com.paper2.domain.Input;
import com.paper2.domain.Patient;
import com.paper2.domain.Schedule;
import com.paper2.domain.Solution;
import com.paper2.domain.TimeObject;
import com.paper2.simulator.SimulationPolicy;
import com.paper2.simulator.Solver;
import com.paper2.simulator.solver.LocalSearchSolver;

/**
 * Core simulation orchestration.
 * <p>
 * Suggested location: {@code com.paper2.simulator} — domain of the simulator,
 * alongside {@code regras}, {@code solver}, etc.
 */
public class PolicyOne implements SimulationPolicy {
    private int indexNextPatient = 0;
    
    @Override
    public Solution apply(Input input) {
        Solver solver = new LocalSearchSolver();
        Solution solution = new Solution(input);
        this.indexNextPatient = input.getAmountOfPorters();

        while (shouldContinueOnlineLoop(input, solution)) {
            int iterationAnchorSeconds = solution.getSimulatorClock().getSeconds();
            solution.getInventoryState().setIterationAnchorSeconds(iterationAnchorSeconds);
            solution.rebuildWorkingInventory();
            solution = solver.solve(solution);
            updateSimulatorClock(solution);
            appendFinalResults(solution);
            solution.getInventoryState().rebuildFinalCommittedFromAllFinalSchedules(solution);
        }

        updateSimulatorClock(solution);
        solution.getInventoryState().setIterationAnchorSeconds(solution.getSimulatorClock().getSeconds());
        solution.rebuildWorkingInventory();


        return solution;
    }

    @Override
    public boolean shouldContinueOnlineLoop(Input input, Solution solution) {
        return this.indexNextPatient <= input.getAmountOfPatients();
    }

    /**
     * Policy 1: mirrors {@code update_final_results_policy_one} in {@code policies.cpp}.
     */
    @Override
    public void appendFinalResults(Solution solution) {
        for (Schedule schedule : solution.getSchedules()) {
            mergeEarlyCompletedPatientsIntoFinal(
                    solution,
                    schedule);
            schedule.resetStartPatient(solution.getTransportedPatients());
        }
        solution.resetNonTransportedPatients();
    }

    

    /**
     * For one porter: copies into the final schedule those patients whose activity on the
     * {@code workingSolution} started before the next job’s requested time.
     *
     * @param solution solution after the scheduler (same reference as in {@link #apply}); also used with
     *        {@link Solution#getPatientById(int)} on the instance catalog
     * @param schedule that porter’s working {@link Schedule} on {@code solution}
     */
    private void mergeEarlyCompletedPatientsIntoFinal(
            Solution solution,
            Schedule schedule) {
        if (!schedule.hasPatients()) {
            return;
        }
        Patient firstScheduled = schedule.getStart().getNext();
        FinalSchedule finalSchedule = solution.getFinalSchedules().get(schedule.getId());

        for (Patient currentPatient = firstScheduled; currentPatient != null; currentPatient = currentPatient
                .getNext()) {
            if (currentPatient.isValid()
                    && !solution.isPatientTransported(currentPatient)) {
                if (currentPatient.startMovingAtMost(solution.getSimulatorClock())) {
                    finalSchedule.getPatients().add(currentPatient);
                    solution.getPatientsToSchedule().remove(currentPatient);
                    solution.getTransportedPatients().add(currentPatient.getId());
                } else {
                    break;
                }
            }
        }
    }

 
    private void updateSimulatorClock(Solution solution) {
        this.indexNextPatient++;
        Patient nextPatient = solution.getPatientById(this.indexNextPatient);
        if (nextPatient == null) {
            solution.setSimulatorClock(maxEndTimeAcrossScheduleEnds(solution));
            return;
        }
        solution.getPatientsToSchedule().add(nextPatient);
        TimeObject nextPatientRequestTime = nextPatient.getRequestedTime();
        solution.setSimulatorClock(nextPatientRequestTime);
    }

    /**
     * Latest end-of-service instant among all schedules: {@code max(schedule.getEnd().getEndTime())} in seconds.
     */
    private static TimeObject maxEndTimeAcrossScheduleEnds(Solution solution) {
        int maxSec = 0;
        if (solution.getSchedules() == null) {
            return new TimeObject(maxSec);
        }
        for (Schedule s : solution.getSchedules()) {
            Patient end = s.getEnd();
            if (end == null) {
                continue;
            }
            TimeObject et = end.getEndTime();
            if (et == null) {
                continue;
            }
            maxSec = Math.max(maxSec, et.getSeconds());
        }
        return new TimeObject(maxSec);
    }
}