package com.paper2.dto.solution;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Aggregated route times ({@code HH:MM:SS}). */
@Data
@NoArgsConstructor
@JsonPropertyOrder({
    "start",
    "end",
    "duration",
    "transportTime",
    "travelTime",
    "idleTime",
    "totalTimeWorkedSeconds",
    "totalTimeWorkedClock"
})
public class ScheduleTimeSummaryDto {
    private String start;
    private String end;
    /** {@code last end} − {@link com.paper2.domain.DomainConstants#SCHEDULE_START_TIME_SECONDS}. */
    private String duration;
    private String transportTime;
    private String travelTime;
    private String idleTime;
    /**
     * {@code solution.getSimulatorClock() − route start} in seconds (route start = same basis as {@link #start}:
     * dummy start or {@link com.paper2.domain.DomainConstants#SCHEDULE_START_TIME_SECONDS}).
     */
    private long totalTimeWorkedSeconds;
    /** Same span as {@link #totalTimeWorkedSeconds}, as duration {@code HH:MM:SS}. */
    private String totalTimeWorkedClock;

    public ScheduleTimeSummaryDto(
            String start,
            String end,
            String duration,
            String transportTime,
            String travelTime,
            String idleTime,
            long totalTimeWorkedSeconds,
            String totalTimeWorkedClock) {
        this.start = start;
        this.end = end;
        this.duration = duration;
        this.transportTime = transportTime;
        this.travelTime = travelTime;
        this.idleTime = idleTime;
        this.totalTimeWorkedSeconds = totalTimeWorkedSeconds;
        this.totalTimeWorkedClock = totalTimeWorkedClock;
    }
}
