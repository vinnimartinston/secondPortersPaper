package com.paper2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing one patient (request) in the simulation / problem instance.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientDto {
    private int id;
    private LocationDto location;
    private PriorityDto priority;
    private TimeDto time;
    private TransportModeDto transportMode; 
}
