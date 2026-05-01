package com.paper2.dto.solution;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary of a wheelchair balance curve over a half-open time window (not a per-second array).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepotInventoryTimelineSummaryDto {

    private int windowStartSeconds;
    private String windowStartClock;
    private int windowEndExclusiveSeconds;
    /** Clock for the last second included in the summary range ({@code windowEndExclusiveSeconds - 1}). */
    private String windowEndLastIncludedClock;

    private long minBalance;
    private long maxBalance;
    private long balanceAtWindowStart;
    private long balanceAtLastIncludedSecond;

    private long wheelchairViolationSecondsBelowZero;

    /**
     * Ordered steps where balance changes (optional; omit or empty for near-constant curves).
     */
    private List<BalanceStepDto> balanceSteps = new ArrayList<>();
}
