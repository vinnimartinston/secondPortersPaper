package com.paper2.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;

/**
 * Domain: schedule of one porter.
 */
@Getter
@Setter
public class Schedule {

    private int id;
    private int amountOfPatients;
    private Porter porter;
    private Patient start;
    private Patient end;
    private ScheduleTimeProperties time;

    public Schedule(Porter porter, Patient dummyPatient) {
        dummyPatient.updateTimeFromLastPatient(new TimeObject(DomainConstants.SCHEDULE_START_TIME_SECONDS), new TimeObject(0));
        dummyPatient.setNext(null);
        dummyPatient.setPrevious(null);
        this.id = porter.getId();
        this.amountOfPatients = 0;
        this.porter = porter;
        this.start = dummyPatient;
        this.end = dummyPatient;
        this.time = new ScheduleTimeProperties();
    }

    public void resetStartPatient(Set<Integer> transportedPatients) {
        if (!this.getStart().hasNext()) {
            return;
        }
        Patient firstPatient = this.getStart().getNext();
        if (!firstPatient.isValid()) {
            return;
        }
        Patient newStartPatient = this.getStart();
        for (Patient currentPatient = firstPatient; currentPatient != null && currentPatient.isValid(); currentPatient = currentPatient
            .getNext()) {
            if (transportedPatients.contains(currentPatient.getId())) {
                newStartPatient = currentPatient;
            } else {
                break;
            }
            
        }
        this.resetSchedule(newStartPatient);
    }

    public boolean shouldVerifyPatient(Patient patient) {
        return patient != null && patient.isValid() && !patient.equals(this.getEnd());
    }
    
    /**
     * Collapses the working chain to a single anchor node: {@code start == end == patient}, with
     * {@code patient.getNext() == null} (and {@code previous} cleared) so the next insertion attaches after
     * {@link #start} via {@link #insertPatient}.
     */
    public void resetSchedule(Patient patient) {
        if (patient == null) {
            return;
        }
        this.amountOfPatients = 0;
        this.start = patient;
        this.end = patient;
        patient.setNext(null);
        patient.setPrevious(null);
        this.time = new ScheduleTimeProperties(patient.getEndTime());
    }

    /**
     * Patients from {@link #getStart()} along {@code next} links (typically dummy at index 0), in a new list.
     */
    public List<Patient> orderedPatientsFromStart() {
        List<Patient> nodes = new ArrayList<>();
        for (Patient p = this.start; p != null; p = p.getNext()) {
            nodes.add(p);
        }
        return nodes;
    }

    public void insertPatient(Patient patient, Graph graph) {
        insertPatient(patient, graph, null, null, null);
    }

    /**
     * Like {@link #insertPatient(Patient, Graph)}; when {@code depots}, {@code porter}, and {@code depotForLeg} are
     * set and {@link Porter#shouldGoToDepot} applies, travel uses {@link Graph#getTravelTimeBetweenTwoLocations(
     * Location, Location, boolean, Depot)} with that depot (e.g. from {@link com.paper2.metrics.inventory.DepotSelectionByObjective}).
     */
    public void insertPatient(
            Patient patient, Graph graph, List<Depot> depots, Porter porter, Depot depotForLegWhenDepotVisit) {
        Patient predecessor = this.amountOfPatients == 0 ? this.start : this.end;

        predecessor.setNext(patient);
        patient.setPrevious(predecessor);
        this.end = patient;

        TimeObject travelTime =
                RouteTiming.travelBetweenPatients(
                        graph, predecessor, patient, porter, depotForLegWhenDepotVisit);
        patient.updateTimeFromLastPatient(predecessor.getEndTime(), travelTime);

        this.time.setEndTime(patient.getEndTime());
        this.amountOfPatients++;
    }

    /**
     * Removes the last inserted real patient from the chain (trial rollback). Does not restore previous timings;
     * callers should re-run {@link Solution#removeIdle()} on the owning solution.
     *
     * @throws IllegalStateException if there is no scheduled patient to remove
     */
    public void undoLastPatientInsert() {
        if (this.amountOfPatients <= 0) {
            throw new IllegalStateException("No scheduled patient to remove");
        }
        Patient last = this.end;
        Patient predecessor = last.getPrevious();
        if (predecessor == null) {
            throw new IllegalStateException("Broken schedule chain");
        }
        predecessor.setNext(null);
        last.setPrevious(null);
        this.end = predecessor;
        this.amountOfPatients--;
        this.time.setEndTime(predecessor.getEndTime());
    }

    /** Removes all patients after the dummy; resets the porter to initial state (like “zero” in C++). */
    public void clearScheduledPatients() {
        this.start.setNext(null);
        this.start.setPrevious(null);
        this.amountOfPatients = 0;
        this.end = this.start;
        this.time = new ScheduleTimeProperties(this.time.getEndTime());
    }

    /**
     * Appends {@code patient} at the tail without recomputing times (preserves snapshot from the
     * temporary solution).
     */
    public void appendPatientPreserveTimes(Patient patient) {
        patient.setNext(null);
        Patient predecessor = this.amountOfPatients == 0 ? this.start : this.end;
        predecessor.setNext(patient);
        patient.setPrevious(predecessor);
        this.end = patient;
        this.amountOfPatients++;
        this.time = new ScheduleTimeProperties(patient.getEndTime());
    }

    public boolean hasPatients() {
        return this.amountOfPatients > 0;
    }

    public void setEndTime(TimeObject endTime) {
        this.time.setEndTime(endTime);
    }

    @Override
    public String toString() {
        return "Schedule(id: "
                + id
                + ", #Patients:"
                + amountOfPatients
                + ", endTime:"
                + time.getEndTime().toString()
                + ")";
    }
}
