package com.paper2.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.paper2.domain.DomainConstants;
import com.paper2.domain.inventory.DepotBalanceTimeline;
import com.paper2.dto.solution.DepotInventoryTimelineSummaryDto;

class DepotInventoryHorizonSummarizerTest {

    @Test
    void summarizeWorkingHorizon_countsViolationsWhenNegative() {
        DepotBalanceTimeline working = new DepotBalanceTimeline(0);
        int anchor = DomainConstants.SCHEDULE_START_TIME_SECONDS;
        working.addDelta(anchor, -5);
        DepotInventoryTimelineSummaryDto s =
                DepotInventoryHorizonSummarizer.summarizeWorkingHorizon(3, working, anchor, anchor + 3);
        assertEquals(-2, s.getBalanceAtLastIncludedSecond());
        assertEquals(3, s.getWheelchairViolationSecondsBelowZero());
    }
}
