package com.paper2.simulator.solver.localsearch;

import com.paper2.domain.Schedule;
import com.paper2.domain.Solution;
import com.paper2.metrics.WheelchairDepotViolationSecondsCalculator;

/**
 * Routing objective components: unweighted tardiness plus a soft penalty for wheelchair depot under-stock
 * (seconds of negative balance weighted by {@link Solution#getDepotInventoryViolationPenaltyCoefficient()}).
 */
public final class TotalUnweightedTardinessObjective {

    private TotalUnweightedTardinessObjective() {}

    /**
     * Sum of tardiness (seconds) over all non-dummy patients on all working schedules (no depot term).
     */
    public static long evaluate(Solution solution) {
        if (solution.getSchedules() == null) {
            return 0;
        }
        long sum = 0;
        for (Schedule schedule : solution.getSchedules()) {
            sum += LocalSearchScheduleSupport.tardinessSumOnSchedule(schedule);
        }
        return sum;
    }

    /**
     * Unweighted tardiness plus {@code depotViolationSeconds × depotInventoryViolationPenaltyCoefficient}.
     */
    public static long evaluateCombined(Solution solution) {
        long tardinessSumSeconds = evaluate(solution);
        long depotViolationSeconds =
                WheelchairDepotViolationSecondsCalculator.totalViolationSecondsAcrossDepots(solution);
        long penaltyTerm = solution.getDepotInventoryViolationPenaltyCoefficient();
        return tardinessSumSeconds + penaltyTerm * depotViolationSeconds;
    }

    /**
     * Writes {@link Solution#setObjectiveValue(int)} from {@link #evaluateCombined(Solution)}, clamped to {@code int}.
     */
    public static void syncSolutionField(Solution solution) {
        long combinedObjective = evaluateCombined(solution);
        if (combinedObjective > Integer.MAX_VALUE) {
            solution.setObjectiveValue(Integer.MAX_VALUE);
        } else if (combinedObjective < Integer.MIN_VALUE) {
            solution.setObjectiveValue(Integer.MIN_VALUE);
        } else {
            solution.setObjectiveValue((int) combinedObjective);
        }
    }
}
