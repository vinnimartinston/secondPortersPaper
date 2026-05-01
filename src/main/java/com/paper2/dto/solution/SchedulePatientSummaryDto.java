package com.paper2.dto.solution;

import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Patient counts on one porter’s route. */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"transportedPatientCount", "hospitalBedCount", "patientsByPriority"})
public class SchedulePatientSummaryDto {
    private int transportedPatientCount;
    private int hospitalBedCount;
    private Map<Integer, Integer> patientsByPriority;

    public SchedulePatientSummaryDto(
            int transportedPatientCount, int hospitalBedCount, TreeMap<Integer, Integer> patientsByPriority) {
        this.transportedPatientCount = transportedPatientCount;
        this.hospitalBedCount = hospitalBedCount;
        this.patientsByPriority = patientsByPriority;
    }
}
