package com.paper2.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Domain: request time window (from {@link com.paper2.dto.TimeDto}).
 */
@AllArgsConstructor
@Getter
@Setter
public class ScheduleTimeProperties {
    private TimeObject endTime;
    
    public ScheduleTimeProperties() {
        this.endTime = new TimeObject(DomainConstants.SCHEDULE_START_TIME_SECONDS);
    }
}
