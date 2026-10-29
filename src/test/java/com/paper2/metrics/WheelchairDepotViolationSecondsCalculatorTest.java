package com.paper2.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paper2.metrics.inventory.WheelchairDepotEdgeRules.WheelchairDepotBalanceChange;

class WheelchairDepotViolationSecondsCalculatorTest {

    @Test
    void violationSecondsMatchPerSecondWalk() {
        assertEquals(0L, count(1, List.of(), 0, 10));
        assertEquals(5L, count(-1, List.of(), 0, 5));
        assertEquals(3L, count(0, List.of(new WheelchairDepotBalanceChange(2, -1)), 0, 5));
        assertEquals(0L, count(1, List.of(new WheelchairDepotBalanceChange(2, -1)), 0, 5));
        assertEquals(
                3L,
                count(
                        1,
                        List.of(
                                new WheelchairDepotBalanceChange(1, -1),
                                new WheelchairDepotBalanceChange(1, -1),
                                new WheelchairDepotBalanceChange(4, 1)),
                        0,
                        6));
        assertEquals(
                1L,
                WheelchairDepotViolationSecondsCalculator.balanceAfterSecond(
                        2, List.of(new WheelchairDepotBalanceChange(2, -1)), 2));
        assertEquals(
                2L,
                WheelchairDepotViolationSecondsCalculator.balanceAfterSecond(
                        2, List.of(new WheelchairDepotBalanceChange(2, -1)), 1));
    }

    private static long count(long initial, List<WheelchairDepotBalanceChange> changes, int start, int end) {
        long fast =
                WheelchairDepotViolationSecondsCalculator.violationSecondsForDepot(
                        initial, changes, start, end);
        assertEquals(brute(initial, changes, start, end), fast);
        return fast;
    }

    private static long brute(
            long initial, List<WheelchairDepotBalanceChange> changes, int start, int end) {
        List<WheelchairDepotBalanceChange> events = new ArrayList<>(changes);
        long violation = 0L;
        for (int second = start; second < end; second++) {
            for (WheelchairDepotBalanceChange change : events) {
                if (change.timeSeconds() == second) {
                    initial += change.wheelchairDelta();
                }
            }
            if (initial < 0) {
                violation++;
            }
        }
        return violation;
    }
}
