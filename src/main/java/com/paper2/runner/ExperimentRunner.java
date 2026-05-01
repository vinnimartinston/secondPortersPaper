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
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.paper2.domain.Solution;
import com.paper2.dto.InputDto;
import com.paper2.dto.metrics.ExperimentMetricsDto;
import com.paper2.metrics.SolutionMetricsCalculator;
import com.paper2.serialization.SolutionSerializer;
import com.paper2.simulator.SimulationRunResult;
import com.paper2.simulator.Simulator;

/**
 * Batch driver: reads input JSON files, runs {@link Simulator} in parallel, and writes
 * {@code *_solution.json} and {@code *_metrics.json} to the output folder.
 *
 * @see ExperimentManager
 * @see ExperimentRunnerConfig
 */
public class ExperimentRunner {
    private static final int DEFAULT_PENALTY_COEFFICIENT = 100_000;

    private final ObjectMapper objectMapper;

    private final Simulator simulator;

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
     * For each configured experiment, resolves input folder from {@code amountOfDepots}
     * ({@code files/input/<x>_depot}), filters by {@link ExperimentManager#resolveInstanceFileNames()}
     * when set, and processes each file in parallel with {@link ForkJoinPool}.
     * <p>
     * Per instance writes: solution ({@link SolutionSerializer#write(Path, com.paper2.domain.Solution)})
     * and metrics ({@link SolutionMetricsCalculator#compute}).
     *
     * @throws Exception propagated I/O or pool interruption
     */
    public void runExperiments() throws Exception {
        ExperimentConfig experimentConfig = resolveExperimentConfig();
        String depotFolder = experimentConfig.amountOfDepots + "_depot";
        Path inputDir =
                Paths.get(ExperimentManager.INPUT_ROOT).resolve(depotFolder);
        Path outputDir = Paths.get("files/output").resolve(experimentConfig.experimentName).resolve(depotFolder);
        int penaltyCoefficient = experimentConfig.penaltyCoefficient;
        Solution.setDefaultDepotInventoryViolationPenaltyCoefficient(penaltyCoefficient);

        if (!Files.isDirectory(inputDir)) {
                throw new IllegalArgumentException("input directory is not valid: " + inputDir);
        }
        Files.createDirectories(outputDir);

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

        System.out.printf(
                "Running %d instance(s) | parallelism=%d | penalty=%d | out=%s%n",
                jsonFiles.size(), parallelism, penaltyCoefficient, outputDir.toAbsolutePath());

        try (ForkJoinPool pool = new ForkJoinPool(parallelism)) {
            pool.submit(() -> jsonFiles.parallelStream().forEach(path -> runOne(path, outputDir)))
                    .get();
        }
    }

    /**
     * Runs one instance: reads {@link InputDto}, runs simulation, writes solution and metrics JSON.
     *
     * @param inputPath input JSON file
     * @param outputDir output directory
     */
    private void runOne(Path inputPath, Path outputDir) {
        try {
            InputDto input = objectMapper.readValue(inputPath.toFile(), InputDto.class);
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
        } catch (IOException e) {
            throw new UncheckedIOException("Failed processing " + inputPath, e);
        }
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

    private static String stripJsonExtension(String fileName) {
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            return fileName.substring(0, fileName.length() - 5);
        }
        return fileName;
    }

    private ExperimentConfig resolveExperimentConfig() {
        List<String> experimentFiles = ExperimentManager.resolveExperimentFileNames();
        if (experimentFiles.isEmpty()) {
            return new ExperimentConfig("experiment", DEFAULT_PENALTY_COEFFICIENT, 0);
        }
        String firstExperiment = stripJsonExtension(experimentFiles.get(0));
        Path experimentsDir = Paths.get(ExperimentManager.resolveExperimentsFolder());
        Path experimentPath = experimentsDir.resolve(experimentFiles.get(0)).normalize();
        if (!experimentPath.startsWith(experimentsDir.normalize()) || !Files.isRegularFile(experimentPath)) {
            return new ExperimentConfig(firstExperiment, DEFAULT_PENALTY_COEFFICIENT, 0);
        }
        try {
            ExperimentFileConfig cfg =
                    objectMapper.readValue(experimentPath.toFile(), ExperimentFileConfig.class);
            Integer penalty = cfg.getPenalty();
            Integer amountOfDepots = cfg.getAmountOfDepots();
            int normalizedPenalty =
                    (penalty == null || penalty <= 0) ? DEFAULT_PENALTY_COEFFICIENT : penalty;
            int normalizedAmountOfDepots = amountOfDepots == null ? 0 : amountOfDepots;
            return new ExperimentConfig(firstExperiment, normalizedPenalty, normalizedAmountOfDepots);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed reading experiment config " + experimentPath, e);
        }
    }

    private static final class ExperimentConfig {
        private final String experimentName;
        private final int penaltyCoefficient;
        private final int amountOfDepots;

        private ExperimentConfig(String experimentName, int penaltyCoefficient, int amountOfDepots) {
            this.experimentName = experimentName;
            this.penaltyCoefficient = penaltyCoefficient;
            this.amountOfDepots = amountOfDepots;
        }
    }

    private static class ExperimentFileConfig {
        private Integer penalty;
        private Integer amountOfDepots;

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
    }
}
