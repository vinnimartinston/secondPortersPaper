package com.paper2.domain;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.paper2.domain.inventory.SolutionInventoryState;
import com.paper2.metrics.inventory.DepotSelectionByObjective;
import com.paper2.metrics.inventory.WheelchairDepotEdgeRules;

import lombok.Getter;
import lombok.Setter;

/**
 * Domain: result of one simulation run (from {@link com.paper2.dto.SimulationOutputDto}).
 */
@Getter
@Setter
public class Solution {
    private static int defaultDepotInventoryViolationPenaltyCoefficient = 100_000;
    private InstanceMetadata metadata;
    private List<Schedule> schedules;
    private List<Depot> depots;
    private Graph graph;
    private List<Patient> patients;
    private Set<Integer> transportedPatients;
    private List<Patient> patientsToSchedule;
    private List<FinalSchedule> finalSchedules;
    private Set<Integer> priorities;
    private TimeObject simulatorClock;
    private int objectiveValue;
    /**
     * Multiplier applied to total seconds of negative wheelchair balance at depots when scoring
     * {@link #objectiveValue} (soft penalty; not a hard constraint).
     */
    private int depotInventoryViolationPenaltyCoefficient = defaultDepotInventoryViolationPenaltyCoefficient;
    /** Committed and per-iteration working wheelchair timelines at depots (soft inventory). */
    private SolutionInventoryState inventoryState;

    /**
     * Static patient definition in this instance (same list as source {@link Input}), or {@code null} if
     * the id does not exist.
     */
    public Patient getPatientById(int id) {
        if (patients == null) {
            return null;
        }
        return patients.stream().filter(p -> p.getId() == id).findFirst().orElse(null);
    }

    public Solution(Input input) {
        this.simulatorClock = new TimeObject(DomainConstants.SCHEDULE_START_TIME_SECONDS);
        this.metadata = input.getMetadata();
        this.depots = input.getDepots();
        this.graph = input.getGraph();
        this.patients = input.getPatients();
        this.transportedPatients = new LinkedHashSet<>();
        this.patientsToSchedule = createPatientsToScheduleList(input);
        this.priorities = this.createPrioritiesSet(input);
        this.schedules = this.createSchedulesList(input);
        this.finalSchedules = this.createFinalSchedulesList(input);
        this.objectiveValue = 0;
        this.depotInventoryViolationPenaltyCoefficient = defaultDepotInventoryViolationPenaltyCoefficient;
        this.inventoryState = new SolutionInventoryState(input.getDepots());
        this.inventoryState.setIterationAnchorSeconds(this.simulatorClock.getSeconds());
    }

    public static void setDefaultDepotInventoryViolationPenaltyCoefficient(int coefficient) {
        defaultDepotInventoryViolationPenaltyCoefficient = coefficient;
    }


    private List<Patient> createPatientsToScheduleList(Input input) {
        return input.getPatients().stream().filter(patient -> patient.getRequestedTime().getSeconds() == this.simulatorClock.getSeconds()).collect(Collectors.toList());
    }

    private Set<Integer> createPrioritiesSet(Input input) {
        return input.getPatients().stream().map(Patient::getPriority).map(Priority::getPriority).collect(Collectors.toSet());
    }

    private List<FinalSchedule> createFinalSchedulesList(Input input) {
        return IntStream.range(0, input.getAmountOfPorters()).mapToObj(i -> new FinalSchedule(new Porter(i), input.getDummyPatient())).collect(Collectors.toList());
    }

    private List<Schedule> createSchedulesList(Input input) {
        return IntStream.range(0, input.getAmountOfPorters()).mapToObj(i -> new Schedule(new Porter(i), new Patient(input.getDummyPatient()))).collect(Collectors.toList());
    }

    /**
     * Remove idle time on each porter schedule by left-shifting jobs (from the 2nd onward),
     * analogue to {@code remove_idle} in the reference C++: for each machine {@code k} and each
     * job index {@code i_job >= 1},
     * {@code start = max(release_date[current], time_instant, end_previous)} and
     * {@code end = start + setup(prev, current) + processing(current)}.
     * <p>
     * The first real patient after the dummy is left unchanged (same as not updating vector index 0).
     */
    public void removeIdle() {
        DepotSelectionByObjective.DepotLegPlan depotLegPlan =
                DepotSelectionByObjective.buildPlan(this, null);
        for (Schedule schedule : schedules) {
            removeIdleOnSchedule(schedule, depotLegPlan);
        }
    }

    /**
     * Updates <em>this</em> solution (e.g. {@code finalSolution}) from {@code input} and the temporary
     * {@code workingSolution}, mirroring {@code update_current_results} in C++: per porter, clears the
     * chain; if there are jobs on the working schedule, copies each with request data from {@link Input}
     * and times from the working schedule; otherwise records only that porter’s dummy job.
     */
    public void updateCurrentResults(Input input, Solution workingSolution) {
        if (schedules == null
                || workingSolution.getSchedules() == null
                || schedules.size() != workingSolution.getSchedules().size()) {
            throw new IllegalArgumentException("incompatible schedules between solutions");
        }
        for (Schedule schedule : workingSolution.getSchedules()) {
            Schedule finalSchedule = schedules.get(schedule.getId());
            finalSchedule.clearScheduledPatients();

            Patient patient = schedule.getStart().getNext();
            if (patient != null) {
                for (Patient walker = patient; walker != null; walker = walker.getNext()) {
                    finalSchedule.appendPatientPreserveTimes(new Patient(walker));
                }
            } else {
                finalSchedule.appendPatientPreserveTimes(new Patient(input.getDummyPatient()));
            }
        }
    }

    private void removeIdleOnSchedule(Schedule schedule, DepotSelectionByObjective.DepotLegPlan depotLegPlan) {
        Patient first = schedule.getStart();
        if (first == null) {
            return;
        }

        int legIndexForTimes = 0;
        Patient current = first.getNext();
        while (current != null) {
            Patient previous = current.getPrevious();
            TimeObject earliestReady =
                    RouteTiming.earliestStart(
                            current.getRequestedTime(), previous.getEndTime(), this.simulatorClock);
            legIndexForTimes++;
            Depot depotForLeg =
                    DepotSelectionByObjective.depotForLeg(
                            depotLegPlan,
                            schedule.getId(),
                            legIndexForTimes,
                            previous,
                            this.depots,
                            this.graph);
            TimeObject travelTime =
                    RouteTiming.travelBetweenPatients(
                            this.graph, previous, current, schedule.getPorter(), depotForLeg);
            current.updateTimeFromLastPatient(earliestReady, travelTime);
            current = current.getNext();
        }
        int legIndex = 0;
        for (Patient p = schedule.getStart(); p != null; p = p.getNext()) {
            Patient next = p.getNext();
            if (next != null) {
                legIndex++;
                int depotId =
                        WheelchairDepotEdgeRules.depotIdVisitedBeforeNext(
                                p,
                                next,
                                schedule.getPorter(),
                                this.depots,
                                this.graph,
                                schedule.getId(),
                                legIndex,
                                depotLegPlan);
                p.setDepotIdVisitedBeforeNext(depotId);
            } else {
                p.setDepotIdVisitedBeforeNext(0);
            }
        }
        schedule.setEndTime(schedule.getEnd().getEndTime());
    }

    /**
     * One line per {@link Schedule} (no indent), then one line per patient in the chain (from
     * {@code start.getNext()}), each prefixed with {@code \t}.
     */
    @Override
    public String toString() {
        if (schedules == null || schedules.isEmpty()) {
            return "Solution(schedules=empty)";
        }
        StringBuilder sb = new StringBuilder();
        DepotSelectionByObjective.DepotLegPlan linePlan =
                graph != null && depots != null && !depots.isEmpty()
                        ? DepotSelectionByObjective.buildPlan(this, null)
                        : null;
        for (Schedule schedule : schedules) {
            sb.append(schedule.toString()).append('\n');
            int lineLeg = 0;
            for (Patient p = schedule.getStart(); p != null; p = p.getNext()) {
                if (p.isDummy()) {
                    continue;
                }
                Patient prev = p.getPrevious();
                if (prev != null) {
                    lineLeg++;
                    Porter porter = schedule.getPorter();
                    Depot depotForLine =
                            linePlan != null && porter != null
                                    ? DepotSelectionByObjective.depotForLeg(
                                            linePlan,
                                            schedule.getId(),
                                            lineLeg,
                                            prev,
                                            this.depots,
                                            this.graph)
                                    : null;
                    TimeObject travel =
                            RouteTiming.travelBetweenPatients(graph, prev, p, porter, depotForLine);
                    sb.append('\t')
                            .append("Travel Time: ")
                            .append(travel.toString())
                            .append('\n');
                }
                sb.append('\t').append(p.toStringWithTime()).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public boolean isPatientTransported(Patient patient){
        return this.transportedPatients.contains(patient.getId());
    }

    public void resetNonTransportedPatients() {
        for (Patient patient : this.patientsToSchedule) {
            if (!this.transportedPatients.contains(patient.getId())) {
                patient.resetPatient();
            }
        }
    }

    /**
     * Recomputes per-iteration working wheelchair deltas from current {@link #schedules} and
     * {@link SolutionInventoryState#getIterationAnchorSeconds()}.
     */
    public void rebuildWorkingInventory() {
        if (inventoryState != null) {
            inventoryState.rebuildWorkingFromWorkingSchedules(this);
        }
    }
}
