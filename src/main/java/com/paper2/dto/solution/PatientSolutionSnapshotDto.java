package com.paper2.dto.solution;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.paper2.dto.LocationDto;
import com.paper2.dto.PriorityDto;
import com.paper2.dto.TransportModeDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Patient in the solution: position on the route ({@code sequence} ≥ 1), request data + {@link PatientSolutionTimesDto}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "sequence", "time", "transportMode", "priority", "location"})
public class PatientSolutionSnapshotDto {
    private int id;
    /** Position in the service sequence (1 = first real patient on the route). */
    private int sequence;
    private PatientSolutionTimesDto time;
    private TransportModeDto transportMode;
    private PriorityDto priority;
    private LocationDto location;
}
