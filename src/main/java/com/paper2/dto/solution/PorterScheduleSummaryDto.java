package com.paper2.dto.solution;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-porter schedule summary: {@link #patient} (counts) and {@link #time} (instants and durations).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"patient", "time"})
public class PorterScheduleSummaryDto {
    private SchedulePatientSummaryDto patient;
    private ScheduleTimeSummaryDto time;
}
