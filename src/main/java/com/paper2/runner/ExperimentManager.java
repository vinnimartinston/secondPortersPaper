package com.paper2.runner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Path constants and helpers that turn {@link ExperimentRunnerConfig} lists into concrete experiment/instance file names.
 *
 * <ul>
 *   <li>{@link #INPUT_ROOT}: instance JSON directory ({@code files/input}); {@link ExperimentRunner} reads instances here.</li>
 *   <li>{@link #EXPERIMENTS_ROOT}: experiment JSON directory ({@code files/experiments}); resolved names include {@code .json}.</li>
 *   <li>{@link #resolveInstanceFileNames()}: normalizes {@link ExperimentRunnerConfig#getInstances()} with optional {@code .json} suffix.</li>
 *   <li>{@link #resolveExperimentFileNames()}: normalizes {@link ExperimentRunnerConfig#getExperiments()}, or lists every
 *       {@code *.json} in {@link #EXPERIMENTS_ROOT} when that config list is empty (sorted).</li>
 * </ul>
 */
public final class ExperimentManager {

    /** Relative path to instance problem JSON files ({@code files/input}). */
    public static final String INPUT_ROOT = "files/input";

    /** Relative path to experiment definition JSON files ({@code files/experiments}). */
    public static final String EXPERIMENTS_ROOT = "files/experiments";

    private ExperimentManager() {
    }

    /** Returns {@link #EXPERIMENTS_ROOT} (same value; indirection kept for callers that resolve folders dynamically). */
    public static String resolveExperimentsFolder() {
        return EXPERIMENTS_ROOT;
    }

    /**
     * Normalized instance file names with a {@code .json} suffix.
     *
     * <p>Returns an empty list when {@link ExperimentRunnerConfig#getInstances()} is null or empty; {@link ExperimentRunner}
     * interprets that as “all instance JSON files” in {@link #INPUT_ROOT}.
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

    /**
     * Experiment JSON file names ({@code .json} suffix ensured).
     * If {@link ExperimentRunnerConfig#getExperiments()} is null or empty, returns every {@code *.json}
     * file name under {@link #EXPERIMENTS_ROOT}, sorted lexicographically.
     */
    public static List<String> resolveExperimentFileNames() {
        List<String> raw = ExperimentRunnerConfig.getExperiments();
        if (raw == null || raw.isEmpty()) {
            return listAllExperimentJsonFileNames();
        }
        return raw.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(ExperimentManager::ensureJsonSuffix)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Lists regular {@code *.json} file names under {@link #EXPERIMENTS_ROOT}, sorted lexicographically; returns an empty
     * list if the directory is missing.
     *
     * @throws UncheckedIOException if the directory cannot be read
     */
    private static List<String> listAllExperimentJsonFileNames() {
        Path dir = Paths.get(EXPERIMENTS_ROOT);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    names.add(p.getFileName().toString());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed listing experiments under " + dir, e);
        }
        return names.stream().sorted().collect(Collectors.toList());
    }

    /** Appends {@code .json} when {@code name} does not already end with that suffix (case-insensitive). */
    private static String ensureJsonSuffix(String name) {
        if (name.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return name;
        }
        return name + ".json";
    }
}
