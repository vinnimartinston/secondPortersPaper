package com.paper2.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paper2.domain.Input;
import com.paper2.domain.Solution;
import com.paper2.dto.InputDto;
import com.paper2.dto.metrics.ExperimentMetricsDto;
import com.paper2.dto.solution.SolutionResultDto;
import com.paper2.mapper.InputMapper;
import com.paper2.metrics.SolutionMetricsCalculator;
import com.paper2.serialization.SolutionExportRestorer;

class MetricsRegeneratorTest {

    private static final Path SAMPLE_SOLUTION =
            Paths.get("files/output/default/800N1SC3DEP20RT40CH1REP_solution.json");
    private static final Path SAMPLE_METRICS =
            Paths.get("files/output/default/800N1SC3DEP20RT40CH1REP_metrics.json");
    private static final Path SAMPLE_INPUT =
            Paths.get("files/input/800N1SC3DEP20RT40CH1REP.json");

    @Test
    @EnabledIf("sampleArtifactsExist")
    void restoredSolutionMatchesExistingMetricsSummary() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InputDto inputDto = mapper.readValue(SAMPLE_INPUT.toFile(), InputDto.class);
        SolutionResultDto exported = mapper.readValue(SAMPLE_SOLUTION.toFile(), SolutionResultDto.class);
        ExperimentMetricsDto existing = mapper.readValue(SAMPLE_METRICS.toFile(), ExperimentMetricsDto.class);

        int penalty = exported.getObjectiveFunction().getDepotInventoryViolationPenaltyCoefficient();
        Input input = InputMapper.toDomain(inputDto);
        Solution solution = SolutionExportRestorer.restore(input, exported, penalty);
        ExperimentMetricsDto regenerated =
                SolutionMetricsCalculator.compute(
                        solution, inputDto, existing.getSimulatorWallTimeSeconds());

        JsonNode expectedSummary = mapper.valueToTree(existing.getSummary());
        JsonNode actualSummary = mapper.valueToTree(regenerated.getSummary());
        assertEquals(expectedSummary, actualSummary);
        assertEquals(existing.getTransportedPatientCount(), regenerated.getTransportedPatientCount());
    }

    @Test
    @EnabledIf("sampleArtifactsExist")
    void regenerateAllPreservesWallTime() throws Exception {
        Path tempDir = Files.createTempDirectory("metrics-regen-test");
        Path experimentDir = tempDir.resolve("default");
        Files.createDirectories(experimentDir);
        Files.copy(SAMPLE_SOLUTION, experimentDir.resolve(SAMPLE_SOLUTION.getFileName()));
        Files.copy(SAMPLE_METRICS, experimentDir.resolve(SAMPLE_METRICS.getFileName()));

        double originalWall =
                new ObjectMapper()
                        .readValue(SAMPLE_METRICS.toFile(), ExperimentMetricsDto.class)
                        .getSimulatorWallTimeSeconds();

        MetricsRegenerator.RegenerationSummary summary = new MetricsRegenerator().regenerateAll(tempDir);
        assertEquals(1, summary.wrote());

        ExperimentMetricsDto rewritten =
                new ObjectMapper()
                        .readValue(
                                experimentDir.resolve("800N1SC3DEP20RT40CH1REP_metrics.json").toFile(),
                                ExperimentMetricsDto.class);
        assertEquals(originalWall, rewritten.getSimulatorWallTimeSeconds(), 1e-9);
        assertTrue(rewritten.getSummary().getScheduleDurationSeconds() > 0);
    }

    static boolean sampleArtifactsExist() {
        return Files.isRegularFile(SAMPLE_SOLUTION)
                && Files.isRegularFile(SAMPLE_METRICS)
                && Files.isRegularFile(SAMPLE_INPUT);
    }
}
