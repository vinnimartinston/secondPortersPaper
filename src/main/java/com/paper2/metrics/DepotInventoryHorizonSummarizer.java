package com.paper2.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToLongFunction;

import com.paper2.domain.DomainConstants;
import com.paper2.domain.TimeObject;
import com.paper2.domain.inventory.DepotBalanceTimeline;
import com.paper2.dto.solution.BalanceStepDto;
import com.paper2.dto.solution.DepotInventoryTimelineSummaryDto;

/**
 * Builds compact {@link DepotInventoryTimelineSummaryDto} snapshots (min/max, violations, optional steps).
 */
public final class DepotInventoryHorizonSummarizer {

    private static final int MAX_STEPS_IN_JSON = 400;

    private DepotInventoryHorizonSummarizer() {}

    /**
     * Committed inventory only: {@link DepotBalanceTimeline#balanceAtOrBefore(int)} at each second.
     */
    public static DepotInventoryTimelineSummaryDto summarizeFinalTimeline(
            DepotBalanceTimeline finalTimeline, int windowStartSeconds, int windowEndExclusiveSeconds) {
        if (windowEndExclusiveSeconds <= windowStartSeconds) {
            return emptySummary(windowStartSeconds, windowEndExclusiveSeconds);
        }
        IntToLongFunction balanceAtT = t -> finalTimeline.balanceAtOrBefore(t);
        return summarize(balanceAtT, windowStartSeconds, windowEndExclusiveSeconds);
    }

    /**
     * Working horizon: balance at {@code anchor} from committed stock plus working-only deltas up to each second.
     */
    public static DepotInventoryTimelineSummaryDto summarizeWorkingHorizon(
            long balanceAfterCommittedAtAnchor,
            DepotBalanceTimeline workingOnlyDeltas,
            int anchorSeconds,
            int windowEndExclusiveSeconds) {
        if (windowEndExclusiveSeconds <= anchorSeconds) {
            return emptySummary(anchorSeconds, windowEndExclusiveSeconds);
        }
        var deltas = workingOnlyDeltas.getNetDeltaAtSecond();
        IntToLongFunction balanceAtT =
                t -> {
                    long b = balanceAfterCommittedAtAnchor;
                    for (var e : deltas.headMap(new TimeObject(t), true).entrySet()) {
                        if (e.getKey().getSeconds() >= anchorSeconds) {
                            b += e.getValue();
                        }
                    }
                    return b;
                };
        return summarize(balanceAtT, anchorSeconds, windowEndExclusiveSeconds);
    }

    private static DepotInventoryTimelineSummaryDto emptySummary(int start, int end) {
        DepotInventoryTimelineSummaryDto dto = new DepotInventoryTimelineSummaryDto();
        dto.setWindowStartSeconds(start);
        dto.setWindowEndExclusiveSeconds(Math.max(start, end));
        dto.setWindowStartClock(formatHms(start));
        dto.setWindowEndLastIncludedClock(end > start ? formatHms(end - 1) : formatHms(start));
        dto.setMinBalance(0);
        dto.setMaxBalance(0);
        dto.setBalanceAtWindowStart(0);
        dto.setBalanceAtLastIncludedSecond(0);
        dto.setWheelchairViolationSecondsBelowZero(0);
        dto.setBalanceSteps(List.of());
        return dto;
    }

    private static DepotInventoryTimelineSummaryDto summarize(
            IntToLongFunction balanceAtEndOfSecond, int windowStartSeconds, int windowEndExclusiveSeconds) {
        DepotInventoryTimelineSummaryDto dto = new DepotInventoryTimelineSummaryDto();
        dto.setWindowStartSeconds(windowStartSeconds);
        dto.setWindowEndExclusiveSeconds(windowEndExclusiveSeconds);
        dto.setWindowStartClock(formatHms(windowStartSeconds));
        dto.setWindowEndLastIncludedClock(formatHms(windowEndExclusiveSeconds - 1));

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        long violationSeconds = 0;

        long balanceAtStart = balanceAtEndOfSecond.applyAsLong(windowStartSeconds);
        dto.setBalanceAtWindowStart(balanceAtStart);

        List<BalanceStepDto> steps = new ArrayList<>();
        Long previous = null;
        for (int t = windowStartSeconds; t < windowEndExclusiveSeconds; t++) {
            long b = balanceAtEndOfSecond.applyAsLong(t);
            min = Math.min(min, b);
            max = Math.max(max, b);
            if (b < 0) {
                violationSeconds++;
            }
            if (previous == null || b != previous) {
                if (steps.size() < MAX_STEPS_IN_JSON) {
                    steps.add(new BalanceStepDto(t, formatHms(t), b));
                }
                previous = b;
            }
        }

        int lastSecond = windowEndExclusiveSeconds - 1;
        dto.setBalanceAtLastIncludedSecond(balanceAtEndOfSecond.applyAsLong(lastSecond));
        dto.setMinBalance(min == Long.MAX_VALUE ? balanceAtStart : min);
        dto.setMaxBalance(max == Long.MIN_VALUE ? balanceAtStart : max);
        dto.setWheelchairViolationSecondsBelowZero(violationSeconds);
        dto.setBalanceSteps(steps.size() > MAX_STEPS_IN_JSON ? List.of() : steps);
        return dto;
    }

    private static String formatHms(int secondsSinceMidnight) {
        int sec = Math.min(Math.max(0, secondsSinceMidnight), DomainConstants.MAX_TIME_SECONDS);
        return new TimeObject(sec).toString();
    }
}
