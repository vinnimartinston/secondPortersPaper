package com.paper2.domain.inventory;

import java.util.Map;
import java.util.TreeMap;

import com.paper2.domain.TimeObject;

/**
 * Piecewise-constant wheelchair balance for one depot: domain initial stock plus net deltas at instants
 * ({@link TimeObject} seconds since midnight), applied from that second onward.
 */
public final class DepotBalanceTimeline {

    private final int domainInitialBalance;
    private final TreeMap<TimeObject, Integer> netDeltaAtSecond = new TreeMap<>();

    public DepotBalanceTimeline(int domainInitialBalance) {
        this.domainInitialBalance = domainInitialBalance;
    }

    public int getDomainInitialBalance() {
        return domainInitialBalance;
    }

    public void clearDeltas() {
        netDeltaAtSecond.clear();
    }

    public void addDelta(int second, int delta) {
        addDelta(new TimeObject(second), delta);
    }

    /** Net wheelchair change applies from this instant onward (same convention as {@link #addDelta(int, int)}). */
    public void addDelta(TimeObject instant, int delta) {
        if (delta == 0) {
            return;
        }
        netDeltaAtSecond.merge(instant, delta, Integer::sum);
    }

    /**
     * Stock at the end of second {@code second}, i.e. after applying all deltas with {@code timeSeconds <= second}.
     */
    public long balanceAtOrBefore(int second) {
        return balanceAtOrBefore(new TimeObject(second));
    }

    /** Same as {@link #balanceAtOrBefore(int)} using a {@link TimeObject} bound. */
    public long balanceAtOrBefore(TimeObject instant) {
        long balance = domainInitialBalance;
        for (Map.Entry<TimeObject, Integer> e : netDeltaAtSecond.headMap(instant, true).entrySet()) {
            balance += e.getValue();
        }
        return balance;
    }

    public TreeMap<TimeObject, Integer> getNetDeltaAtSecond() {
        return netDeltaAtSecond;
    }
}
