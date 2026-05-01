package com.paper2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Priority and weight of a patient.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriorityDto {
    private int priority;
    private int weight;
}
