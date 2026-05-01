package com.paper2.runner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.paper2.domain.Solution;
import com.paper2.dto.DepotDto;
import com.paper2.dto.InputDto;
import com.paper2.dto.metrics.ExperimentMetricsDto;
import com.paper2.metrics.SolutionMetricsCalculator;
import com.paper2.serialization.SolutionSerializer;
import com.paper2.simulator.SimulationRunResult;
import com.paper2.simulator.Simulator;

/**
 * Batch driver for simulation experiments.
 *
 * <p>For each experiment JSON (see {@link ExperimentManager#resolveExperimentFileNames()}), loads penalty and
 * optional wheelchair inventory overrides, then runs every selected instance JSON under {@link ExperimentManager#INPUT_ROOT}
 * through {@link Simulator} using a {@link ForkJoinPool}. Writes solution and metrics JSON files named after each
 * instance stem under {@code files/output/}, in a subfolder named after the experiment file stem (without {@code .json}).
 *
 * <p>Before each instance starts, prints a cyan progress line {@code Running Experiment XX/YY (ZZ%)} on the console
 * ({@code ZZ = round(100·XX/YY)}). {@code XX}/{@code YY} count all instance runs across every experiment file when several
 * are configured; with parallelism greater than one, lines may appear out of order. After each instance finishes successfully,
 * prints a green line {@code Experiment <name> + <instanceStem> - ok}.
 *
 * @see ExperimentManager
 * @see ExperimentRunnerConfig
 */
public class ExperimentRunner {
    private static final int DEFAULT_PENALTY_COEFFICIENT = 100_000;

    /** ANSI cyan foreground (terminal progress line before each instance). */
    private static final String ANSI_CYAN = "\u001B[36m";

    /** ANSI green foreground (terminal line after each instance completes). */
    private static final String ANSI_GREEN = "\u001B[32m";

    private static final String ANSI_RESET = "\u001B[0m";

    private static final Object INSTANCE_PROGRESS_PRINT_LOCK = new Object();

    private final ObjectMapper objectMapper;

    private final Simulator simulator;

    /** Constructs a runner with default {@link ObjectMapper} (modules registered, indented JSON on write) and {@link Simulator}. */
    public ExperimentRunner() {
        this(new ObjectMapper(), new Simulator());
    }

    /**
     * @param objectMapper Jackson mapper (module registration and indented output enabled)
     * @param simulator simulation engine to use for each instance
     */
    public ExperimentRunner(ObjectMapper objectMapper, Simulator simulator) {
        this.objectMapper = objectMapper.findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.simulator = simulator;
    }

    /**
     * Entry point: {@code java -cp ... com.paper2.runner.ExperimentRunner} or Maven exec.
     * <p>
     * Optional arguments and effective folders come from {@link ExperimentManager} /
     * {@link ExperimentRunnerConfig}.
     *
     * @param args not used directly (configuration via properties / manager)
     * @throws Exception if {@link #runExperiments()} fails
     */
    public static void main(String[] args) throws Exception {
        new ExperimentRunner().runExperiments();
    }

    /**
     * Runs all experiments returned by {@link ExperimentManager#resolveExperimentFileNames()}, sequentially per
     * experiment file but parallel across instances within each experiment.
     *
     * <p>Instance inputs are read from {@link ExperimentManager#INPUT_ROOT}. If
     * {@link ExperimentManager#resolveInstanceFileNames()} is non-empty, only those JSON names are run (paths must stay
     * under the input root); if empty, every {@code *.json} file in that folder is run (sorted).
     *
     * <p>If no experiment JSON exists under {@link ExperimentManager#EXPERIMENTS_ROOT}, prints a message and returns.
     * If there are no instance JSON files under the input root, prints a message and returns.
     *
     * @throws Exception if directory listing fails or the pool task is interrupted
     */
    public void runExperiments() throws Exception {
        Path inputDir = Paths.get(ExperimentManager.INPUT_ROOT);

        if (!Files.isDirectory(inputDir)) {
                throw new IllegalArgumentException("input directory is not valid: " + inputDir);
        }

        List<String> experimentFiles = ExperimentManager.resolveExperimentFileNames();
        if (experimentFiles.isEmpty()) {
            Path experimentsRoot = Paths.get(ExperimentManager.EXPERIMENTS_ROOT);
            System.out.println(
                    "No experiment JSON files under: " + experimentsRoot.toAbsolutePath());
            return;
        }

        List<Path> jsonFiles = listInputJsonFiles(
                inputDir, ExperimentManager.resolveInstanceFileNames());
        if (jsonFiles.isEmpty()) {
            System.out.println("No JSON files to run under: " + inputDir.toAbsolutePath());
            return;
        }

        Integer configuredParallelism = ExperimentRunnerConfig.getParallelism();
        int parallelism = (configuredParallelism != null && configuredParallelism > 0)
                ? configuredParallelism
                : Runtime.getRuntime().availableProcessors();

        int instancesPerExperiment = jsonFiles.size();
        int totalInstancesGlobal = instancesPerExperiment * experimentFiles.size();

        try (ForkJoinPool pool = new ForkJoinPool(parallelism)) {
            int experimentOrdinal = 0;
            for (String experimentJsonFileName : experimentFiles) {
                ExperimentConfig experimentConfig = resolveExperimentConfig(experimentJsonFileName);
                Path outputDir = Paths.get("files/output").resolve(experimentConfig.experimentName);
                int penaltyCoefficient = experimentConfig.penaltyCoefficient;
                Solution.setDefaultDepotInventoryViolationPenaltyCoefficient(penaltyCoefficient);
                Files.createDirectories(outputDir);

                System.out.printf(
                        "Experiment %s | %d instance(s) | parallelism=%d | penalty=%d | out=%s%n",
                        experimentConfig.experimentName,
                        jsonFiles.size(),
                        parallelism,
                        penaltyCoefficient,
                        outputDir.toAbsolutePath());

                final int experimentIndexZeroBased = experimentOrdinal++;
                pool.submit(
                                () ->
                                        IntStream.range(0, instancesPerExperiment)
                                                .parallel()
                                                .forEach(
                                                        i ->
                                                                runOne(
                                                                        jsonFiles.get(i),
                                                                        outputDir,
                                                                        experimentConfig,
                                                                        experimentIndexZeroBased * instancesPerExperiment
                                                                                + i
                                                                                + 1,
                                                                        totalInstancesGlobal)))
                        .get();
            }
        }
    }

    /**
     * Loads one instance JSON, optionally overrides depot wheelchair counts from the experiment config, runs the
     * simulator, and writes solution and metrics artifacts.
     *
     * @param inputPath path to the instance JSON under {@link ExperimentManager#INPUT_ROOT}
     * @param outputDir directory for this experiment’s outputs (typically under {@code files/output/})
     * @param experimentConfig resolved experiment settings (penalty applied globally before this batch)
     * @param instanceIndexOneBased global index of this instance across all experiments (1 … {@code totalInstances}), for logging
     * @param totalInstances total instance runs ({@code jsonFiles × experimentFiles})
     */
    private void runOne(
            Path inputPath,
            Path outputDir,
            ExperimentConfig experimentConfig,
            int instanceIndexOneBased,
            int totalInstances) {
        printInstanceProgress(instanceIndexOneBased, totalInstances);
        try {
            InputDto input = objectMapper.readValue(inputPath.toFile(), InputDto.class);
            applyWheelchairInventoryOverrides(
                    input,
                    experimentConfig.overwriteInitialWheelchairInventory(),
                    experimentConfig.initialWheelchairInventoryByDepotId());
            long t0 = System.nanoTime();
            SimulationRunResult run = simulator.start(input);
            double wallSeconds = (System.nanoTime() - t0) / 1_000_000_000.0;
            String stem = baseName(inputPath);
            if (run.getSolution() != null) {
                Path solutionFile = outputDir.resolve(stem + "_solution.json");
                SolutionSerializer.write(solutionFile, run.getSolution());
                System.out.println("Wrote " + solutionFile.toAbsolutePath());
                ExperimentMetricsDto metrics =
                        SolutionMetricsCalculator.compute(run.getSolution(), input, wallSeconds);
                Path metricsFile = outputDir.resolve(stem + "_metrics.json");
                objectMapper.writeValue(metricsFile.toFile(), metrics);
                System.out.println("Wrote " + metricsFile.toAbsolutePath());
            }
            printExperimentInstanceOk(experimentConfig.experimentName, stem);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed processing " + inputPath, e);
        }
    }

    /**
     * Prints {@code Running Experiment XX/YY (ZZ%)} in ANSI cyan (foreground {@code 36}); {@code ZZ} is
     * {@code round(100·XX/YY)} for the batch position {@code XX}.
     */
    private static void printInstanceProgress(int instanceIndexOneBased, int totalInstances) {
        int zz =
                totalInstances <= 0
                        ? 0
                        : (int) Math.round(100.0 * instanceIndexOneBased / totalInstances);
        synchronized (INSTANCE_PROGRESS_PRINT_LOCK) {
            System.out.printf(
                    "%sRunning Experiment %d/%d (%d%%)%s%n",
                    ANSI_CYAN,
                    instanceIndexOneBased,
                    totalInstances,
                    zz,
                    ANSI_RESET);
        }
    }

    /**
     * Prints {@code Experiment <experimentName> + <instanceStem> - ok} in ANSI green after a finished instance run.
     */
    private static void printExperimentInstanceOk(String experimentName, String instanceStem) {
        synchronized (INSTANCE_PROGRESS_PRINT_LOCK) {
            System.out.printf(
                    "%sExperiment %s - %s - OK!%s%n",
                    ANSI_GREEN,
                    experimentName,
                    instanceStem,
                    ANSI_RESET);
        }
    }

    /**
     * When {@code overwrite} is true, replaces {@link DepotDto#getInitialWheelchairInventory()} on each depot whose
     * {@code id} appears in {@code overrides} with the experiment-declared value (input JSON otherwise unchanged).
     */
    private static void applyWheelchairInventoryOverrides(
            InputDto input, boolean overwrite, Map<Integer, Integer> overrides) {
        if (!overwrite || overrides.isEmpty()) {
            return;
        }
        List<DepotDto> depots = input.getDepots();
        if (depots == null) {
            return;
        }
        for (DepotDto d : depots) {
            Integer inv = overrides.get(d.getId());
            if (inv != null) {
                d.setInitialWheelchairInventory(inv);
            }
        }
    }

    /**
     * Builds a depot-id → inventory map from experiment JSON when overwrite is requested; skips null patch entries or
     * null {@code id}/{@code initialWheelchairInventory}.
     */
    private static Map<Integer, Integer> buildWheelchairInventoryOverrides(
            Boolean overwriteFlag, List<ExperimentDepotInventoryPatch> depots) {
        if (!Boolean.TRUE.equals(overwriteFlag) || depots == null || depots.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Integer> map = new LinkedHashMap<>();
        for (ExperimentDepotInventoryPatch p : depots) {
            if (p == null || p.id() == null || p.initialWheelchairInventory() == null) {
                continue;
            }
            map.put(p.id(), p.initialWheelchairInventory());
        }
        return Map.copyOf(map);
    }

    /**
     * Lists {@code *.json} files in the folder or only the given names.
     *
     * @param inputDir input folder
     * @param instanceFileNames names with {@code .json}; if empty, all {@code *.json} in the folder
     * @return sorted paths
     * @throws IOException on directory read failure
     */
    private static List<Path> listInputJsonFiles(Path inputDir, List<String> instanceFileNames)
            throws IOException {
        if (instanceFileNames == null || instanceFileNames.isEmpty()) {
            List<Path> all = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir, "*.json")) {
                for (Path p : stream) {
                    if (Files.isRegularFile(p)) {
                        all.add(p);
                    }
                }
            }
            return all.stream().sorted().collect(Collectors.toList());
        }

        List<Path> chosen = new ArrayList<>();
        for (String fileName : instanceFileNames) {
            if (fileName == null || fileName.isBlank()) {
                continue;
            }
            Path candidate = inputDir.resolve(fileName.trim()).normalize();
            if (!candidate.startsWith(inputDir.normalize())) {
                throw new IllegalArgumentException("Instance path escapes input folder: " + fileName);
            }
            if (Files.isRegularFile(candidate)) {
                chosen.add(candidate);
            }
        }
        return chosen.stream().sorted().collect(Collectors.toList());
    }

    /** File name without the {@code .json} extension. */
    private static String baseName(Path path) {
        return stripJsonExtension(path.getFileName().toString());
    }

    /** Removes a trailing {@code .json} suffix case-insensitively; otherwise returns {@code fileName} unchanged. */
    private static String stripJsonExtension(String fileName) {
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return fileName.substring(0, fileName.length() - 5);
        }
        return fileName;
    }

    /**
     * Reads experiment JSON under {@link ExperimentManager#EXPERIMENTS_ROOT} with the given file name and derives runnable settings.
     * Missing or unreadable files fall back to {@link ExperimentConfig#defaultConfig(String)} using the stem as name.
     *
     * @param experimentJsonFileName file name including {@code .json}
     */
    private ExperimentConfig resolveExperimentConfig(String experimentJsonFileName) {
        String firstExperiment = stripJsonExtension(experimentJsonFileName);
        Path experimentsDir = Paths.get(ExperimentManager.resolveExperimentsFolder());
        Path experimentPath = experimentsDir.resolve(experimentJsonFileName).normalize();
        if (!experimentPath.startsWith(experimentsDir.normalize()) || !Files.isRegularFile(experimentPath)) {
            return ExperimentConfig.defaultConfig(firstExperiment);
        }
        try {
            ExperimentFileConfig cfg =
                    objectMapper.readValue(experimentPath.toFile(), ExperimentFileConfig.class);
            Integer penalty = cfg.getPenalty();
            Integer amountOfDepots = cfg.getAmountOfDepots();
            int normalizedPenalty =
                    (penalty == null || penalty <= 0) ? DEFAULT_PENALTY_COEFFICIENT : penalty;
            int normalizedAmountOfDepots = amountOfDepots == null ? 0 : amountOfDepots;
            Map<Integer, Integer> wheelchairOverrides =
                    buildWheelchairInventoryOverrides(
                            cfg.getOverWriteInitialWheelchairInventory(), cfg.getDepots());
            boolean overwriteWheelchairs =
                    Boolean.TRUE.equals(cfg.getOverWriteInitialWheelchairInventory())
                            && !wheelchairOverrides.isEmpty();
            return new ExperimentConfig(
                    firstExperiment,
                    normalizedPenalty,
                    normalizedAmountOfDepots,
                    overwriteWheelchairs,
                    wheelchairOverrides);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed reading experiment config " + experimentPath, e);
        }
    }

    /** Fragment of experiment JSON: optional depot {@code id} and {@code initialWheelchairInventory} for overrides. */
    private record ExperimentDepotInventoryPatch(Integer id, Integer initialWheelchairInventory) {}

    /** Resolved experiment parameters passed into each {@link #runOne} invocation for one batch of instances. */
    private static final class ExperimentConfig {
        private final String experimentName;
        private final int penaltyCoefficient;
        private final int amountOfDepots;
        private final boolean overwriteInitialWheelchairInventory;
        private final Map<Integer, Integer> initialWheelchairInventoryByDepotId;

        private ExperimentConfig(
                String experimentName,
                int penaltyCoefficient,
                int amountOfDepots,
                boolean overwriteInitialWheelchairInventory,
                Map<Integer, Integer> initialWheelchairInventoryByDepotId) {
            this.experimentName = experimentName;
            this.penaltyCoefficient = penaltyCoefficient;
            this.amountOfDepots = amountOfDepots;
            this.overwriteInitialWheelchairInventory = overwriteInitialWheelchairInventory;
            this.initialWheelchairInventoryByDepotId = initialWheelchairInventoryByDepotId;
        }

        /** Fallback when the experiment file is missing or invalid: default penalty and no wheelchair overrides. */
        static ExperimentConfig defaultConfig(String experimentName) {
            return new ExperimentConfig(
                    experimentName, DEFAULT_PENALTY_COEFFICIENT, 0, false, Map.of());
        }

        boolean overwriteInitialWheelchairInventory() {
            return overwriteInitialWheelchairInventory;
        }

        Map<Integer, Integer> initialWheelchairInventoryByDepotId() {
            return initialWheelchairInventoryByDepotId;
        }
    }

    /** Jackson-bound shape of an experiment JSON file (penalty, optional depot patches). */
    private static class ExperimentFileConfig {
        private Integer penalty;
        private Integer amountOfDepots;
        private Boolean overWriteInitialWheelchairInventory;
        private List<ExperimentDepotInventoryPatch> depots;

        public Integer getPenalty() {
            return penalty;
        }

        public void setPenalty(Integer penalty) {
            this.penalty = penalty;
        }

        public Integer getAmountOfDepots() {
            return amountOfDepots;
        }

        public void setAmountOfDepots(Integer amountOfDepots) {
            this.amountOfDepots = amountOfDepots;
        }

        public Boolean getOverWriteInitialWheelchairInventory() {
            return overWriteInitialWheelchairInventory;
        }

        public void setOverWriteInitialWheelchairInventory(Boolean overWriteInitialWheelchairInventory) {
            this.overWriteInitialWheelchairInventory = overWriteInitialWheelchairInventory;
        }

        public List<ExperimentDepotInventoryPatch> getDepots() {
            return depots;
        }

        public void setDepots(List<ExperimentDepotInventoryPatch> depots) {
            this.depots = depots;
        }
    }
}
