package com.paper2.metrics;

import java.util.List;

import com.paper2.domain.FinalSchedule;
import com.paper2.domain.Patient;
import com.paper2.domain.Solution;
import com.paper2.metrics.inventory.DepotSelectionByObjective;

/**
 * Objective-function terms computed from {@link Solution#getFinalSchedules()} after the full online plan is
 * committed (all transported patients), for export in {@code *_solution.json} and {@code *_metrics.json}.
 */
public final class FinalScheduleObjectiveTerms {

    private FinalScheduleObjectiveTerms() {}

    /**
     * @param sumWeightedTardiness Σ lateness seconds × priority weight on final routes
     * @param sumUnweightedTardinessSeconds Σ raw lateness seconds on final routes
     * @param totalWheelchairViolationSecondsBelowZero depot violation seconds (final routes)
     * @param depotPenaltyTerm {@code totalWheelchairViolationSecondsBelowZero × penalty coefficient}
     * @param objectiveValue {@code sumWeightedTardiness + depotPenaltyTerm}
     */
    public record Result(
            long sumWeightedTardiness,
            long sumUnweightedTardinessSeconds,
            long totalWheelchairViolationSecondsBelowZero,
            long depotPenaltyTerm,
            long objectiveValue) {}

    public static Result compute(Solution solution) {
        if (solution == null || solution.getFinalSchedules() == null) {
            return new Result(0, 0, 0, 0, 0);
        }

        long sumWeighted = 0;
        long sumUnweighted = 0;
        for (FinalSchedule fs : solution.getFinalSchedules()) {
            if (fs.getPatients() == null) {
                continue;
            }
            for (Patient p : fs.getPatients()) {
                if (p == null || p.isDummy()) {
                    continue;
                }
                int lateSec = p.getTime().getLateness().getSeconds();
                int weight = p.getPriority() != null ? p.getPriority().getWeight() : 0;
                sumWeighted += (long) lateSec * weight;
                sumUnweighted += lateSec;
            }
        }

        List<List<Patient>> finalChains =
                DepotSelectionByObjective.chainOverridesFromFinalSchedulesByPorterId(solution);
        long violationSeconds =
                solution.getDepots() == null || solution.getDepots().isEmpty()
                        ? 0L
                        : WheelchairDepotViolationSecondsCalculator.totalViolationSecondsAcrossDepots(
                                solution, finalChains);
        int penaltyCoefficient = solution.getDepotInventoryViolationPenaltyCoefficient();
        long depotPenaltyTerm = violationSeconds * penaltyCoefficient;
        long objectiveValue = sumWeighted + depotPenaltyTerm;

        return new Result(
                sumWeighted, sumUnweighted, violationSeconds, depotPenaltyTerm, objectiveValue);
    }

    public static int clampObjectiveToInt(long objectiveValue) {
        if (objectiveValue > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (objectiveValue < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) objectiveValue;
    }
}
