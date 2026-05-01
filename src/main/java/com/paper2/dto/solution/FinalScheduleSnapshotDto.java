package com.paper2.dto.solution;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One final schedule per porter: real patients only in {@code patients}; shift start in {@code scheduleSummary.time.start}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"porterId", "scheduleSummary", "patients"})
public class FinalScheduleSnapshotDto {
    private int porterId;
    private PorterScheduleSummaryDto scheduleSummary;
    private List<PatientSolutionSnapshotDto> patients = new ArrayList<>();
}
