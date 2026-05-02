package com.paper2.simulator.debug;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.paper2.domain.Patient;
import com.paper2.domain.Schedule;
import com.paper2.domain.Solution;
import com.paper2.domain.TimeObject;
import com.paper2.metrics.WheelchairDepotViolationSecondsCalculator;
import com.paper2.simulator.solver.localsearch.TotalUnweightedTardinessObjective;

/**
 * TEMPORARY diagnostic: appends one CSV row per online step after {@code appendFinalResults}, so you can inspect
 * objective components iteration by iteration. Output: {@code target/objective_iteration_trace.csv} (relative to JVM
 * working directory, usually the Maven project root).
 *
 * <p>Disable with {@code -Dpaper2.objectiveIterationTrace=false}.
 */
public final class ObjectiveIterationTrace {

    private static final String PROP = "paper2.objectiveIterationTrace";

    private static final Path OUTPUT = Path.of("target", "objective_iteration_trace.csv");

    private static final Object LOCK = new Object();

    private static boolean headerWritten;

    private ObjectiveIterationTrace() {}

    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty(PROP, "true"));
    }

    /** Truncates the trace file and writes the CSV header (call once per simulation run). */
    public static void beginTrace() {
        if (!enabled()) {
            return;
        }
        synchronized (LOCK) {
            headerWritten = false;
            try {
                Path parent = OUTPUT.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(
                        OUTPUT,
                        "iteration,simulator_clock_seconds,simulator_clock,solution_objective_int_field,"
                                + "recomputed_total_long,unweighted_tardiness_seconds,"
                                + "weighted_tardiness_sum,depot_violation_seconds,penalty_coefficient,"
                                + "depot_penalty_term_long\n",
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                headerWritten = true;
            } catch (IOException e) {
                throw new IllegalStateException("Cannot write objective trace: " + OUTPUT, e);
            }
        }
    }

    /**
     * Logs objective breakdown for the current {@link Solution} (working schedules + current inventory model).
     *
     * <ul>
     *   <li>{@code recomputed_total_long} matches {@link TotalUnweightedTardinessObjective#evaluateCombined(Solution)}.
     *   <li>{@code unweighted_tardiness_seconds} is the sum of lateness seconds on non-dummy working jobs (what the solver minimizes for tardiness).
     *   <li>{@code weighted_tardiness_sum} is Σ(lateness_seconds × priority.weight) on the same jobs (informational; the stored objective uses <em>unweighted</em> tardiness).
     *   <li>{@code depot_penalty_term_long} is {@code depot_violation_seconds × penalty_coefficient}.
     * </ul>
     */
    public static void appendAfterAppendFinalResults(int iterationOneBased, Solution solution) {
        if (!enabled() || solution == null) {
            return;
        }
        synchronized (LOCK) {
            if (!headerWritten) {
                beginTrace();
            }
            long unweighted = TotalUnweightedTardinessObjective.evaluate(solution);
            long weightedSum = weightedTardinessWeightedSum(solution);
            long depotViolSec = WheelchairDepotViolationSecondsCalculator.totalViolationSecondsAcrossDepots(solution);
            int coef = solution.getDepotInventoryViolationPenaltyCoefficient();
            long depotTerm = coef * depotViolSec;
            long recombined = TotalUnweightedTardinessObjective.evaluateCombined(solution);
            int clockSec =
                    solution.getSimulatorClock() != null ? solution.getSimulatorClock().getSeconds() : -1;
            String clockHms = clockSec < 0 ? "" : new TimeObject(clockSec).toString();
            String line =
                    String.format(
                            "%d,%d,%s,%d,%d,%d,%d,%d,%d,%d%n",
                            iterationOneBased,
                            clockSec,
                            clockHms,
                            solution.getObjectiveValue(),
                            recombined,
                            unweighted,
                            weightedSum,
                            depotViolSec,
                            coef,
                            depotTerm);
            try (BufferedWriter w =
                    Files.newBufferedWriter(
                            OUTPUT,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND)) {
                w.write(line);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot append objective trace: " + OUTPUT, e);
            }
        }
    }

    private static long weightedTardinessWeightedSum(Solution solution) {
        if (solution.getSchedules() == null) {
            return 0;
        }
        long sum = 0;
        for (Schedule schedule : solution.getSchedules()) {
            for (Patient p = schedule.getStart(); p != null; p = p.getNext()) {
                if (p.isDummy()) {
                    continue;
                }
                int late = p.getTime().getLateness().getSeconds();
                int w = p.getPriority() != null ? p.getPriority().getWeight() : 0;
                sum += (long) late * w;
            }
        }
        return sum;
    }
}
