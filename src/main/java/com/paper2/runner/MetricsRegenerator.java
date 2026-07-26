package com.paper2.runner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.paper2.domain.Input;
import com.paper2.domain.Solution;
import com.paper2.dto.DepotDto;
import com.paper2.dto.InputDto;
import com.paper2.dto.metrics.ExperimentMetricsDto;
import com.paper2.dto.solution.SolutionObjectiveFunctionDto;
import com.paper2.dto.solution.SolutionResultDto;
import com.paper2.mapper.InputMapper;
import com.paper2.metrics.SolutionMetricsCalculator;
import com.paper2.serialization.SolutionExportRestorer;

/**
 * Recomputes {@code *_metrics.json} under {@code files/output/} (recursively) from existing
 * {@code *_solution.json} plus {@link ExperimentManager#INPUT_ROOT}, preserving
 * {@link ExperimentMetricsDto#getSimulatorWallTimeSeconds()} when the metrics file already exists.
 */
public final class MetricsRegenerator {

    private static final Path OUTPUT_ROOT = Paths.get("files/output");
    private static final int DEFAULT_PENALTY_COEFFICIENT = 100_000;
    private static final String SOLUTION_SUFFIX = "_solution.json";
    private static final String METRICS_SUFFIX = "_metrics.json";

    private final ObjectMapper objectMapper;

    public MetricsRegenerator() {
        this(createMapper());
    }

    MetricsRegenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static void main(String[] args) throws IOException {
        Path outputRoot = args.length > 0 ? Paths.get(args[0]) : OUTPUT_ROOT;
        RegenerationSummary summary = new MetricsRegenerator().regenerateAll(outputRoot);
        System.out.printf(
                "MetricsRegenerator: wrote=%d skipped=%d failed=%d%n",
                summary.wrote(), summary.skipped(), summary.failed());
        if (summary.failed() > 0) {
            System.exit(1);
        }
    }

    /**
     * @param outputRoot typically {@code files/output}
     * @return counts of processed files
     */
    public RegenerationSummary regenerateAll(Path outputRoot) throws IOException {
        if (!Files.isDirectory(outputRoot)) {
            throw new IllegalArgumentException("Output root is not a directory: " + outputRoot);
        }

        int wrote = 0;
        int skipped = 0;
        int failed = 0;
        List<Path> solutionFiles = listSolutionFiles(outputRoot);
        Path inputRoot = Paths.get(ExperimentManager.INPUT_ROOT).toAbsolutePath().normalize();

        for (Path solutionFile : solutionFiles) {
            try {
                Outcome outcome = regenerateOne(solutionFile, inputRoot);
                switch (outcome) {
                    case WROTE -> wrote++;
                    case SKIPPED -> skipped++;
                }
            } catch (RuntimeException e) {
                failed++;
                System.err.println("Failed " + solutionFile + ": " + e.getMessage());
            }
        }
        return new RegenerationSummary(wrote, skipped, failed);
    }

    private Outcome regenerateOne(Path solutionFile, Path inputRoot) throws IOException {
        String fileName = solutionFile.getFileName().toString();
        if (!fileName.endsWith(SOLUTION_SUFFIX)) {
            return Outcome.SKIPPED;
        }

        String instanceStem = fileName.substring(0, fileName.length() - SOLUTION_SUFFIX.length());
        Path inputPath = inputRoot.resolve(instanceStem + ".json").normalize();
        if (!inputPath.startsWith(inputRoot) || !Files.isRegularFile(inputPath)) {
            throw new IllegalArgumentException("Missing input JSON: " + inputPath);
        }

        Path outputDir = solutionFile.getParent();
        String experimentName = outputDir.getFileName().toString();
        Path metricsFile = outputDir.resolve(instanceStem + METRICS_SUFFIX);

        double preservedWallSeconds = readExistingWallTimeSeconds(metricsFile);

        InputDto inputDto = objectMapper.readValue(inputPath.toFile(), InputDto.class);
        ExperimentSettings experiment = loadExperimentSettings(experimentName);
        applyWheelchairInventoryOverrides(
                inputDto,
                experiment.overwriteInitialWheelchairInventory(),
                experiment.initialWheelchairInventoryByDepotId());

        SolutionResultDto exported = objectMapper.readValue(solutionFile.toFile(), SolutionResultDto.class);
        int penaltyCoefficient = resolvePenaltyCoefficient(exported, experiment.penaltyCoefficient());

        Input input = InputMapper.toDomain(inputDto);
        Solution solution = SolutionExportRestorer.restore(input, exported, penaltyCoefficient);
        ExperimentMetricsDto metrics =
                SolutionMetricsCalculator.compute(solution, inputDto, preservedWallSeconds);

        objectMapper.writeValue(metricsFile.toFile(), metrics);
        System.out.println("Wrote " + metricsFile.toAbsolutePath());
        return Outcome.WROTE;
    }

    private double readExistingWallTimeSeconds(Path metricsFile) throws IOException {
        if (!Files.isRegularFile(metricsFile)) {
            return 0;
        }
        ExperimentMetricsDto existing = objectMapper.readValue(metricsFile.toFile(), ExperimentMetricsDto.class);
        return existing.getSimulatorWallTimeSeconds();
    }

    private static int resolvePenaltyCoefficient(SolutionResultDto exported, int experimentPenalty) {
        SolutionObjectiveFunctionDto objective =
                exported != null ? exported.getObjectiveFunction() : null;
        if (objective != null && objective.getDepotInventoryViolationPenaltyCoefficient() > 0) {
            return objective.getDepotInventoryViolationPenaltyCoefficient();
        }
        return experimentPenalty;
    }

    private ExperimentSettings loadExperimentSettings(String experimentName) {
        Path experimentPath =
                Paths.get(ExperimentManager.EXPERIMENTS_ROOT).resolve(experimentName + ".json").normalize();
        if (!Files.isRegularFile(experimentPath)) {
            return ExperimentSettings.defaults();
        }
        try {
            ExperimentFileConfig cfg = objectMapper.readValue(experimentPath.toFile(), ExperimentFileConfig.class);
            Integer penalty = cfg.getPenalty();
            int normalizedPenalty =
                    penalty == null || penalty <= 0 ? DEFAULT_PENALTY_COEFFICIENT : penalty;
            Map<Integer, Integer> wheelchairOverrides = buildWheelchairInventoryOverrides(
                    cfg.getOverWriteInitialWheelchairInventory(), cfg.getDepots());
            return new ExperimentSettings(
                    normalizedPenalty,
                    Boolean.TRUE.equals(cfg.getOverWriteInitialWheelchairInventory()),
                    wheelchairOverrides);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed reading experiment config " + experimentPath, e);
        }
    }

    private static void applyWheelchairInventoryOverrides(
            InputDto input, boolean overwrite, Map<Integer, Integer> overrides) {
        if (!overwrite || overrides.isEmpty()) {
            return;
        }
        List<DepotDto> depots = input.getDepots();
        if (depots == null) {
            return;
        }
        for (DepotDto depot : depots) {
            Integer inventory = overrides.get(depot.getId());
            if (inventory != null) {
                depot.setInitialWheelchairInventory(inventory);
            }
        }
    }

    private static Map<Integer, Integer> buildWheelchairInventoryOverrides(
            Boolean overwriteFlag, List<ExperimentDepotInventoryPatch> depots) {
        if (!Boolean.TRUE.equals(overwriteFlag) || depots == null || depots.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Integer> map = new LinkedHashMap<>();
        for (ExperimentDepotInventoryPatch patch : depots) {
            if (patch == null || patch.id() == null || patch.initialWheelchairInventory() == null) {
                continue;
            }
            map.put(patch.id(), patch.initialWheelchairInventory());
        }
        return Map.copyOf(map);
    }

    private static List<Path> listSolutionFiles(Path outputRoot) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(outputRoot, FileVisitOption.FOLLOW_LINKS)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(SOLUTION_SUFFIX))
                    .sorted()
                    .forEach(files::add);
        }
        return files;
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    private enum Outcome {
        WROTE,
        SKIPPED
    }

    public record RegenerationSummary(int wrote, int skipped, int failed) {}

    private record ExperimentSettings(
            int penaltyCoefficient,
            boolean overwriteInitialWheelchairInventory,
            Map<Integer, Integer> initialWheelchairInventoryByDepotId) {

        static ExperimentSettings defaults() {
            return new ExperimentSettings(DEFAULT_PENALTY_COEFFICIENT, false, Map.of());
        }
    }

    private record ExperimentDepotInventoryPatch(Integer id, Integer initialWheelchairInventory) {}

    private static class ExperimentFileConfig {
        private Integer penalty;
        private Boolean overWriteInitialWheelchairInventory;
        private List<ExperimentDepotInventoryPatch> depots;

        public Integer getPenalty() {
            return penalty;
        }

        public void setPenalty(Integer penalty) {
            this.penalty = penalty;
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
