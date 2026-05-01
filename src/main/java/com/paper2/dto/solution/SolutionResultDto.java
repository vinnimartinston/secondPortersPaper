package com.paper2.dto.solution;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.paper2.dto.InstanceMetadataDto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON snapshot of a {@link com.paper2.domain.Solution}: clock, objective function breakdown, final schedules,
 * then depot inventory detail.
 */
@Data
@NoArgsConstructor
@JsonPropertyOrder({"metadata", "simulatorClock", "objectiveFunction", "finalSchedules", "depotInventory"})
public class SolutionResultDto {
    private InstanceMetadataDto metadata;
    /** Simulation clock at export time, {@code HH:MM:SS} format. */
    private String simulatorClock;
    /** Objective value, horizon, tardiness and depot-penalty terms. */
    private SolutionObjectiveFunctionDto objectiveFunction;
    private List<FinalScheduleSnapshotDto> finalSchedules = new ArrayList<>();
    /** Per-depot wheelchair inventory snapshots (empty depots list if the instance has no depots). */
    private SolutionDepotInventoryDto depotInventory;

    public SolutionResultDto(
            InstanceMetadataDto metadata,
            String simulatorClock,
            SolutionObjectiveFunctionDto objectiveFunction,
            List<FinalScheduleSnapshotDto> finalSchedules,
            SolutionDepotInventoryDto depotInventory) {
        this.metadata = metadata;
        this.simulatorClock = simulatorClock;
        this.objectiveFunction = objectiveFunction;
        this.finalSchedules = finalSchedules != null ? finalSchedules : new ArrayList<>();
        this.depotInventory = depotInventory;
    }
}
