package com.paper2.dto.solution;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Objective function snapshot for {@link SolutionResultDto}: scalar value, evaluation horizon, penalty
 * parameters, tardiness and depot-penalty components (auditing {@link com.paper2.domain.Solution#getObjectiveValue()}).
 */
@Data
@NoArgsConstructor
@JsonPropertyOrder({
    "objectiveValue",
    "evaluationWindowStartSeconds",
    "evaluationWindowStartClock",
    "evaluationWindowEndExclusiveSeconds",
    "evaluationHorizonLastIncludedSecondClock",
    "depotInventoryViolationPenaltyCoefficient",
    "totalWheelchairViolationSecondsBelowZero",
    "depotPenaltyTerm",
    "unweightedTardinessSumSeconds"
})
public class SolutionObjectiveFunctionDto {

    /** Stored objective on the solution (model-specific aggregate). */
    private int objectiveValue;

    /** Seconds since midnight; shift / initial simulator clock ({@link com.paper2.domain.DomainConstants#SCHEDULE_START_TIME_SECONDS}). */
    private int evaluationWindowStartSeconds;
    private String evaluationWindowStartClock;
    /**
     * Half-open horizon end (exclusive), consistent with {@link com.paper2.metrics.WheelchairDepotViolationSecondsCalculator}.
     */
    private int evaluationWindowEndExclusiveSeconds;
    /** Clock for the last second included in the violation integral ({@code evaluationWindowEndExclusiveSeconds - 1}). */
    private String evaluationHorizonLastIncludedSecondClock;
    private int depotInventoryViolationPenaltyCoefficient;
    private long totalWheelchairViolationSecondsBelowZero;
    /** {@code totalWheelchairViolationSecondsBelowZero × depotInventoryViolationPenaltyCoefficient}. */
    private long depotPenaltyTerm;
    /**
     * Sum of unweighted tardiness on working schedules (non-dummy). With depot penalty,
     * {@link com.paper2.domain.Solution#getObjectiveValue()} is {@code min(int max, unweightedTardinessSumSeconds + depotPenaltyTerm)}.
     */
    private long unweightedTardinessSumSeconds;

    /** Matches {@link JsonPropertyOrder} / export shape; single ctor keeps serializer simple. */
    @SuppressWarnings("java:S107")
    public SolutionObjectiveFunctionDto(
            int objectiveValue,
            int evaluationWindowStartSeconds,
            String evaluationWindowStartClock,
            int evaluationWindowEndExclusiveSeconds,
            String evaluationHorizonLastIncludedSecondClock,
            int depotInventoryViolationPenaltyCoefficient,
            long totalWheelchairViolationSecondsBelowZero,
            long depotPenaltyTerm,
            long unweightedTardinessSumSeconds) {
        this.objectiveValue = objectiveValue;
        this.evaluationWindowStartSeconds = evaluationWindowStartSeconds;
        this.evaluationWindowStartClock = evaluationWindowStartClock;
        this.evaluationWindowEndExclusiveSeconds = evaluationWindowEndExclusiveSeconds;
        this.evaluationHorizonLastIncludedSecondClock = evaluationHorizonLastIncludedSecondClock;
        this.depotInventoryViolationPenaltyCoefficient = depotInventoryViolationPenaltyCoefficient;
        this.totalWheelchairViolationSecondsBelowZero = totalWheelchairViolationSecondsBelowZero;
        this.depotPenaltyTerm = depotPenaltyTerm;
        this.unweightedTardinessSumSeconds = unweightedTardinessSumSeconds;
    }
}
