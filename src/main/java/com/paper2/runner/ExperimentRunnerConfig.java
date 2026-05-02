package com.paper2.runner;

import java.util.List;

import lombok.Data;


/**
 * Static knobs for {@link ExperimentRunner}: which instances and experiments to run, and ForkJoinPool parallelism.
 *
 * <p>Instances listed in {@link #instances} are resolved by {@link ExperimentManager#resolveInstanceFileNames()}
 * ({@code .json} appended when missing). An empty {@link #instances} list causes the runner to process every
 * {@code *.json} file under {@link ExperimentManager#INPUT_ROOT}.
 *
 * <p>{@link #experiments} lists experiment JSON file names under {@link ExperimentManager#EXPERIMENTS_ROOT}; when empty,
 * every {@code *.json} there is run (sorted). Each experiment writes under {@code files/output/} using that file’s stem.
 */
@Data
public class ExperimentRunnerConfig {
    /**
     * Base names of instances to run (e.g. {@code "example"} or {@code "example.json"}).
     * If empty, every {@code *.json} under the resolved input folder is executed.
     */
    public static List<String> instances = List.of("800N1SC3DEP20RT40CH1REP");

    /**
     * Experiment JSON files under {@link ExperimentManager#EXPERIMENTS_ROOT} (with or without {@code .json}).
     * If empty, {@link ExperimentRunner} runs every {@code *.json} in that folder (sorted).
     */
    public static List<String> experiments = List.of("infinity", "default");

    /**
     * Parallelism for {@link java.util.concurrent.ForkJoinPool}. If {@code null} or
     * non-positive, {@link Runtime#availableProcessors()} is used.
     */
    public static Integer parallelism = 2;

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
