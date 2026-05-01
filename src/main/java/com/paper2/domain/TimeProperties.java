package com.paper2.domain;

import com.paper2.dto.TimeDto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Domain: request time window (from {@link com.paper2.dto.TimeDto}).
 */
@AllArgsConstructor
@Getter
@Setter
public class TimeProperties {
    private final TimeObject timeAsked;
    private final TimeObject dueDate;
    private final TimeObject transportTime;
    private TimeObject startTime;
    private TimeObject endTime;
    private TimeObject lateness;
    private TimeObject travelTime;
    public TimeProperties(TimeDto dto, int transportTime) {
        this.timeAsked = new TimeObject(dto.getTimeAsked());
        this.dueDate = new TimeObject(dto.getDueDate());
        this.transportTime = new TimeObject(transportTime);
        this.startTime = new TimeObject(0);
        this.endTime = new TimeObject(0);
        this.lateness = new TimeObject(0);
        this.travelTime = new TimeObject(0);
    }

    public TimeProperties() {
        this.timeAsked = new TimeObject(0);
        this.dueDate = new TimeObject(0);
        this.transportTime = new TimeObject(0);
        this.startTime = new TimeObject(DomainConstants.SCHEDULE_START_TIME_SECONDS);
        this.endTime = new TimeObject(DomainConstants.SCHEDULE_START_TIME_SECONDS);
        this.lateness = new TimeObject(0);
        this.travelTime = new TimeObject(0);
    }

    /** Deep copy of the {@link TimeObject} fields (useful in {@code new Patient(Patient)}). */
    public TimeProperties(TimeProperties other) {
        this.timeAsked = new TimeObject(other.getTimeAsked().getSeconds());
        this.dueDate = new TimeObject(other.getDueDate().getSeconds());
        this.transportTime = new TimeObject(other.getTransportTime().getSeconds());
        this.startTime = new TimeObject(other.getStartTime().getSeconds());
        this.endTime = new TimeObject(other.getEndTime().getSeconds());
        this.lateness = new TimeObject(other.getLateness().getSeconds());
        this.travelTime = new TimeObject(other.getTravelTime().getSeconds());
    }

    public void resetTimeProperties() {
        this.startTime = new TimeObject(0);
        this.endTime = new TimeObject(0);
        this.lateness = new TimeObject(0);
        this.travelTime = new TimeObject(0);
    }

    public void updateTime(TimeObject predecessorEndTime, TimeObject travelTime) {
        this.travelTime = travelTime;
        this.startTime = predecessorEndTime.addTime(travelTime);
        this.endTime = this.startTime.addTime(this.transportTime);
        this.lateness = new TimeObject(Math.max(0, this.endTime.getSeconds() - this.dueDate.getSeconds()));
    }

    /** Updates only the end instant (e.g. echo of last patient on {@link Schedule}). */
    public void setEndTimeSeconds(int endTimeSeconds) {
        this.endTime.setSeconds(endTimeSeconds);
    }

    @Override
    public String toString() {
        return "Time(Asked: "
                + timeAsked
                + ", start: "
                + startTime
                + ", end: "
                + endTime
                + ", due: "
                + dueDate
                + ", lateness: "
                + lateness
                + ")";
    }
}
