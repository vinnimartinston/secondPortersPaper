package com.paper2.domain;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

/**
 * Domain: schedule of one porter.
 */
@Getter
public class FinalSchedule {
    private Porter porter;
    private List<Patient> patients;

    public FinalSchedule(Porter porter, Patient dummyPatient) {
        this.porter = porter;
        this.patients = new ArrayList<>();
        this.patients.add(dummyPatient);
    }

    public void restoreTimeProperties() {
        Patient predecessor = this.patients.get(0);
        for (Patient patient : this.patients) {
            if (!patient.isDummy()) {
                patient.updateTimeFromLastPatient(predecessor.getEndTime(),patient.getTravelTime());
            }
            predecessor = patient;
        }
    }

    /**
     * Summary + one tabbed line per patient (no travel times between stops).
     */
    @Override
    public String toString() {
        return toString(null);
    }

    /**
     * Same layout as {@link Solution#toString()} per porter: header, optionally {@code Travel Time} lines
     * between consecutive patients when {@code graph} is not {@code null}.
     */
    public String toString(Graph graph) {
        int count = patients == null ? 0 : patients.size() - 1;
        StringBuilder sb = new StringBuilder();
        sb.append("FinalSchedule(porter: ")
                .append(porter != null ? porter.getId() : "?")
                .append(", #patients: ")
                .append(count)
                .append(")\n");
        if (patients == null) {
            return sb.toString();
        }
        for (Patient patient : this.patients) {
            if (patient.isValid()) {
                sb.append('\t').append("Travel Time: ").append(patient.getTravelTime().toString()).append('\n');
                sb.append('\t').append(patient.toStringWithTime()).append('\n');
            }
        }
        sb.append("\n");
        return sb.toString();
    }
}
