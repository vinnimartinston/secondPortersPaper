package com.paper2.dto.solution;

import com.paper2.dto.LocationDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One depot as embedded in {@link SolutionResultDto}: static instance data plus evaluated soft-inventory outcome.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepotSolutionSnapshotDto {

    private int depotId;
    private LocationDto location;
    /** Wheelchairs available at the evaluation window start (from input). */
    private int initialWheelchairInventory;
    /**
     * Total seconds in the evaluation window when wheelchair balance at this depot was strictly negative
     * (soft model; evaluation horizon is documented under {@link SolutionObjectiveFunctionDto}).
     */
    private long wheelchairViolationSecondsBelowZero;

    /** Optional summary of committed (final-schedule) inventory over time (see {@link DepotInventoryTimelineSummaryDto}). */
    private DepotInventoryTimelineSummaryDto finalTimelineSummary;
}
