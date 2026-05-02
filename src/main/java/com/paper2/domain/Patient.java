package com.paper2.domain;

import com.paper2.dto.PatientDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain: one patient / transport request (from {@link com.paper2.dto.PatientDto}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Patient {
    private static final int DUMMY_PATIENT_ID = 0;
    private int id;
    private Location location;
    private Priority priority;
    private TimeProperties time;
    private MobilityAidPolicy mobilityAidPolicy;
    private Patient next;
    private Patient previous;
    /** Depot visited between this patient and {@link #next}; {@code 0} if none or no successor. */
    private int depotIdVisitedBeforeNext;

    public Patient(PatientDto dto, Graph graph) {
        this.id = dto.getId();
        this.location = new Location(dto.getLocation());
        this.priority = new Priority(dto.getPriority());
        this.time =
                new TimeProperties(dto.getTime(), graph.getTravelTime(this.location).getSeconds());
        this.mobilityAidPolicy = new MobilityAidPolicy(dto.getTransportMode());
        this.depotIdVisitedBeforeNext = 0;
    }

    public Patient(Patient patient) {
        this.id = patient.getId();
        this.location = patient.getLocation();
        this.priority = patient.getPriority();
        this.time = new TimeProperties(patient.getTime());
        this.mobilityAidPolicy = patient.getMobilityAidPolicy();
        this.depotIdVisitedBeforeNext = patient.getDepotIdVisitedBeforeNext();
    }

    public void resetPatient() {
        this.previous = null;
        this.next = null;
        this.time.resetTimeProperties();
        this.depotIdVisitedBeforeNext = 0;
    }

    /**
     * Static data (request, due date, base effort) from {@code inputRow}; computed instants (start, end,
     * tardiness, effective transport) from {@code outputRow} — analogous to C++ {@code update_current_results}.
     */
    public static Patient snapshotFromInputAndOutput(Patient inputRow, Patient outputRow) {
        Patient p = new Patient(inputRow);
        TimeProperties t = p.getTime();
        TimeProperties o = outputRow.getTime();
        t.getStartTime().setSeconds(o.getStartTime().getSeconds());
        t.getEndTime().setSeconds(o.getEndTime().getSeconds());
        t.getLateness().setSeconds(o.getLateness().getSeconds());
        t.getTransportTime().setSeconds(o.getTransportTime().getSeconds());
        p.setDepotIdVisitedBeforeNext(outputRow.getDepotIdVisitedBeforeNext());
        return p;
    }

    public TimeObject getRequestedTime() {
        return this.time.getTimeAsked();
    }

    public String getRequestedTimeString() {
        return this.time.getTimeAsked().toString();
    }

    public int getPriorityValue() {
        return this.priority.getPriority();
    }

    public int getDueDate() {
        return this.time.getDueDate().getSeconds();
    }

    public TimeObject getEndTime() {
        return this.time.getEndTime();
    }

    public TimeObject getStartTime() {
        return this.time.getStartTime();
    }

    public TimeObject getTravelTime() {
        return this.time.getTravelTime();
    }

    public int getTransportTime() {
        return this.time.getTransportTime().getSeconds();
    }

    public void updateTimeFromLastPatient(TimeObject predecessorEndTime, TimeObject travelTime) {
        this.time.updateTime(predecessorEndTime, travelTime);
    }

    public int calculateObjectiveValue() {
        return this.time.getLateness().getSeconds() * this.priority.getWeight();
    }

    @Override
    public String toString() {
        return "Patient(id: " + this.id +  ", priority: " + this.priority.getPriority() +  ", transport: " + this.mobilityAidPolicy.getAidType() + ")";
    }

    public String toStringWithTime() {
        return  this.toString() + "\n\t " + this.time.toString();
    }

    public boolean isValid() {
        return this.getPrevious() != null && !this.isDummy();
    }

    public boolean isDummy() {
        return this.id == DUMMY_PATIENT_ID;
    }

    public boolean startMovingAtMost(TimeObject time) {
        return this.getStartTime().subtractTime(this.getTravelTime()).isAtMost(time);
    }

    public boolean hasNext() {
        return this.getNext() != null;
    }
}
