package com.paper2.simulator;

import java.time.Duration;

/**
 * Wall-clock deadline for the instance running on the current thread.
 * <p>
 * {@link #check()} is a no-op until {@link #armMinutes(int)} is called. The simulation calls it from the
 * search loops so a run stops between moves instead of waiting for the instance to finish.
 */
public final class InstanceDeadline {

    private static final ThreadLocal<Long> DEADLINE_NANOS = new ThreadLocal<>();

    private InstanceDeadline() {}

    /**
     * Arms a deadline measured from now. A non-positive {@code minutes} clears any deadline on this thread.
     */
    public static void armMinutes(int minutes) {
        if (minutes <= 0) {
            DEADLINE_NANOS.remove();
            return;
        }
        DEADLINE_NANOS.set(System.nanoTime() + Duration.ofMinutes(minutes).toNanos());
    }

    /** Removes the deadline from the current thread. */
    public static void clear() {
        DEADLINE_NANOS.remove();
    }

    /**
     * Throws {@link InstanceTimeLimitExceededException} when the armed deadline has passed.
     */
    public static void check() {
        Long deadlineNanos = DEADLINE_NANOS.get();
        if (deadlineNanos != null && System.nanoTime() >= deadlineNanos) {
            throw new InstanceTimeLimitExceededException();
        }
    }
}
