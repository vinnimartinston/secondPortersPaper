package com.paper2.runner;

import java.util.List;

import lombok.Data;


/**
 * Configuration for batch experiment runs.
 * <p>
 * Input: derived from each experiment's {@code amountOfDepots} ({@code files/input/<x>_depot}).
 * Output: derived from {@link #experiments} (one folder per experiment).
 * Instances: stems or file names; the {@code .json} suffix is applied by
 * {@link ExperimentManager#resolveInstanceFileNames()}.
 */
@Data
public class ExperimentRunnerConfig {
    /**
     * Base names of instances to run (e.g. {@code "example"} or {@code "example.json"}).
     * If empty, every {@code *.json} under the resolved input folder is executed.
     */
    public static List<String> instances = List.of("example_input");

    public static List<String> experiments = List.of("example_experiment");

    /**
     * Parallelism for {@link java.util.concurrent.ForkJoinPool}. If {@code null} or
     * non-positive, {@link Runtime#availableProcessors()} is used.
     */
    public static Integer parallelism = 1;

    public static Integer getParallelism() {
        return parallelism;
    }

    public static List<String> getInstances() {
        return instances;
    }

    public static List<String> getExperiments() {
        return experiments;
    }
}
