package com.paper2.dto.solution.chart;

import java.util.ArrayList;
import java.util.List;

import com.paper2.dto.solution.BalanceStepDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One wheelchair balance series over a half-open time window: bounds plus compact steps (piecewise-constant curve).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepotInventoryHorizonSeriesDto {

    private int windowStartSeconds;
    private String windowStartClock;
    private int windowEndExclusiveSeconds;
    /** Clock label for {@code windowEndExclusiveSeconds - 1}. */
    private String windowEndLastIncludedClock;

    private long minBalanceInWindow;
    private long maxBalanceInWindow;

    private List<BalanceStepDto> balanceSteps = new ArrayList<>();
}
