package com.paper2.dto.solution.chart;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Export payload for depot wheelchair inventory over time: JSON suitable for plotting or paired JPEG rendering.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolutionDepotInventoryChartDto {

    private int simulatorClockSeconds;
    private String simulatorClock;

    private int iterationAnchorSeconds;
    private String iterationAnchorClock;

    private int scheduleStartTimeSeconds;
    private String scheduleStartClock;

    private List<DepotInventoryChartDepotDto> depots = new ArrayList<>();
}
