package com.paper2.domain;

/**
 * Shared domain constants (default times, etc.).
 */
public final class DomainConstants {

    private DomainConstants() {}

    /**
     * Default start instant for schedules / clock (seconds since midnight), e.g. 08:00.
     */
    public static final int SCHEDULE_START_TIME_SECONDS = 28800;

    public static final int MAX_TIME_SECONDS = 86399;

    /**
     * Working (temporary) inventory horizon length from the current iteration anchor (simulator clock at iteration
     * start), in seconds (8 hours).
     */
    public static final int WORKING_INVENTORY_HORIZON_SECONDS = 8 * 3600;

    /**
     * Maximum duration of the objective / violation evaluation window (cap on top of schedule end), 12 hours.
     */
    public static final int MAX_EVALUATION_HORIZON_DURATION_SECONDS = 12 * 3600;
}
