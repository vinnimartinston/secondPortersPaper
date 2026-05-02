package com.paper2.dto.solution;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.paper2.dto.LocationDto;
import com.paper2.dto.PriorityDto;
import com.paper2.dto.TransportModeDto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Patient in the solution: position on the route ({@code sequence} ≥ 1), request data + {@link PatientSolutionTimesDto}.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "id",
    "sequence",
    "time",
    "transportMode",
    "priority",
    "location",
    "depotIdVisitedBeforeNext"
})
public class PatientSolutionSnapshotDto {

    /** Explicit all-args constructor; Jackson deserialization uses the Lombok-generated no-args constructor. */
    public PatientSolutionSnapshotDto(
            int id,
            int sequence,
            PatientSolutionTimesDto time,
            TransportModeDto transportMode,
            PriorityDto priority,
            LocationDto location,
            int depotIdVisitedBeforeNext) {
        this.id = id;
        this.sequence = sequence;
        this.time = time;
        this.transportMode = transportMode;
        this.priority = priority;
        this.location = location;
        this.depotIdVisitedBeforeNext = depotIdVisitedBeforeNext;
    }

    private int id;
    /** Position in the service sequence (1 = first real patient on the route). */
    private int sequence;
    private PatientSolutionTimesDto time;
    private TransportModeDto transportMode;
    private PriorityDto priority;
    private LocationDto location;
    /**
     * Depot visited between this patient and the next on the route ({@code finalSchedules} list order);
     * {@code 0} if none.
     */
    private int depotIdVisitedBeforeNext;
}
