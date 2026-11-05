package com.paper2.simulator;

/**
 * Signals that the current instance passed the wall-clock limit armed by {@link InstanceDeadline}.
 */
public class InstanceTimeLimitExceededException extends RuntimeException {

    public InstanceTimeLimitExceededException() {
        super("Instance time limit exceeded");
    }
}
