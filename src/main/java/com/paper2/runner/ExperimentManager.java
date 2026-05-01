package com.paper2.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Normalizes paths and instance names from {@link ExperimentRunnerConfig}.
 * <ul>
 *   <li>Input root: {@link #INPUT_ROOT}; concrete subfolder is derived from each experiment
 *       ({@code amountOfDepots + "_depot"}) in {@link com.paper2.runner.ExperimentRunner}.</li>
 *   <li>Experiment files: {@link #EXPERIMENTS_ROOT} + names from
 *       {@link ExperimentRunnerConfig#getExperiments()}.</li>
 *   <li>{@code .json} appended to each name in {@code instances} when not already present</li>
 * </ul>
 */
public final class ExperimentManager {

    /** Fixed input root in the project. */
    public static final String INPUT_ROOT = "files/input";
    public static final String EXPERIMENTS_ROOT = "files/experiments";

    private ExperimentManager() {
    }

    /** Fixed folder for experiment configuration JSON files. */
    public static String resolveExperimentsFolder() {
        return EXPERIMENTS_ROOT;
    }

    /**
     * Copy of the instance list with {@code .json} ensured on each entry
     * (empty / null names are ignored).
     */
    public static List<String> resolveInstanceFileNames() {
        List<String> raw = ExperimentRunnerConfig.getInstances();
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(ExperimentManager::ensureJsonSuffix)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Copy of configured experiment file names with {@code .json} suffix ensured. */
    public static List<String> resolveExperimentFileNames() {
        List<String> raw = ExperimentRunnerConfig.getExperiments();
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(ExperimentManager::ensureJsonSuffix)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static String ensureJsonSuffix(String name) {
        if (name.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return name;
        }
        return name + ".json";
    }
}
