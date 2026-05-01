package com.paper2.dto.solution;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request + route: all instants and durations as {@code HH:MM:SS}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({
    "timeAsked",
    "travelTime",
    "start",
    "transportTime",
    "end",
    "dueDate",
    "lateness",
    "responseTime"
})
public class PatientSolutionTimesDto {
    private String timeAsked;
    private String travelTime;
    private String start;
    /** Patient transport duration (service). */
    private String transportTime;
    private String end;
    private String dueDate;
    private String lateness;
    /** {@code start - timeAsked} (time until service start after the request). */
    private String responseTime;
}
