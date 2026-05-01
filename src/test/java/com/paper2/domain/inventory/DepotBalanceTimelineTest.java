package com.paper2.domain.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DepotBalanceTimelineTest {

    @Test
    void balanceAtOrBefore_appliesStepsFromInstantOnward() {
        DepotBalanceTimeline t = new DepotBalanceTimeline(10);
        t.addDelta(100, -3);
        t.addDelta(100, -1);
        assertEquals(10, t.balanceAtOrBefore(99));
        assertEquals(6, t.balanceAtOrBefore(100));
        assertEquals(6, t.balanceAtOrBefore(50_000));
    }

    @Test
    void clearDeltas_resetsToInitialOnly() {
        DepotBalanceTimeline t = new DepotBalanceTimeline(7);
        t.addDelta(0, 1);
        t.clearDeltas();
        assertEquals(7, t.balanceAtOrBefore(999));
    }
}
