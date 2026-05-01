package com.paper2.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain aggregate: full problem instance (from {@link com.paper2.dto.InputDto}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Input {

    private String name;
    private InstanceMetadata metadata;
    private int amountOfPorters;
    private List<Patient> patients;
    private List<Depot> depots;
    private Graph graph;


    public Patient getDummyPatient() {
        return this.patients.get(0);
    }

    /** Instance patient by {@code id} (job index), or {@code null} if missing. */
    public Patient getPatientById(int id) {
        if (this.patients == null) {
            return null;
        }
        return this.patients.stream().filter(p -> p.getId() == id).findFirst().orElse(null);
    }

    public int getAmountOfPatients() {
        return this.patients.size();
    }

    /** Largest {@link Patient#getId()} in the instance (useful as job id bound; not the same as {@link #getAmountOfPatients()}). */
    public int getMaxPatientId() {
        if (this.patients == null || this.patients.isEmpty()) {
            return 0;
        }
        return this.patients.stream().mapToInt(Patient::getId).max().orElse(0);
    }

    /** Requested time for the patient with {@code patientId}, or end-of-day if missing. */
    public TimeObject getNextPatientRequestTime(int patientId) {
        Patient p = getPatientById(patientId);
        if (p == null) {
            return new TimeObject(DomainConstants.MAX_TIME_SECONDS);
        }
        return p.getRequestedTime();
    }
}
