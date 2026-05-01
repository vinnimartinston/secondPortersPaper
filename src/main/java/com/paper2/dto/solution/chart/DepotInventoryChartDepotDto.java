package com.paper2.dto.solution.chart;

import com.paper2.dto.LocationDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Per-depot committed and working inventory horizons for chart export. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepotInventoryChartDepotDto {

    private int depotId;
    private LocationDto location;

    /** Inventory from merged {@link com.paper2.domain.FinalSchedule} routes (committed timeline). */
    private DepotInventoryHorizonSeriesDto finalCommitted;

    /** Inventory from working {@link com.paper2.domain.Schedule} routes over the 8h iteration horizon. */
    private DepotInventoryHorizonSeriesDto workingIteration;
}
