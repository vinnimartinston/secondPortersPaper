package com.paper2.domain;

import com.paper2.dto.PriorityDto;

import lombok.Getter;

/**
 * Domain: priority and weight of a patient request (from {@link com.paper2.dto.PriorityDto}).
 */
@Getter
public class Priority {
    private int priority;
    private int weight;

    public Priority(PriorityDto dto) {
        this.priority = dto.getPriority();
        this.weight = dto.getWeight();
    }
}
