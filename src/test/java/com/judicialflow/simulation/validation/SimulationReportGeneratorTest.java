package com.judicialflow.simulation.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.judicialflow.common.enums.CaseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class SimulationReportGeneratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimulationReportGenerator generator = new SimulationReportGenerator(objectMapper);

    @Test
    @DisplayName("Assert generated markdown Key Findings numbers match JSON numbers dynamically")
    void testGeneratedMarkdownMatchesJsonNumbers(@TempDir Path tempDir) throws Exception {
        SimulationConfig config = new SimulationConfig();
        List<Long> seeds = List.of(42L, 101L, 202L);

        // Build mock scenario results
        MetricSummary statDelayRR = MetricSummary.builder()
                .metricName("Arrivals Statutory Mean Delay (days)")
                .fcfsMean(15.81)
                .engineMean(4.09)
                .pairedDiffMean(-11.71)
                .ci95Lower(-12.61)
                .ci95Upper(-10.82)
                .outcome("Improved")
                .build();

        MetricSummary statDelayTiered = MetricSummary.builder()
                .metricName("Arrivals Statutory Mean Delay (days)")
                .fcfsMean(3.73)
                .engineMean(4.09)
                .pairedDiffMean(0.36)
                .ci95Lower(0.33)
                .ci95Upper(0.40)
                .outcome("Worse")
                .build();

        MetricSummary civilWaitTiered = MetricSummary.builder()
                .metricName("CIVIL P90 Wait (days)")
                .fcfsMean(84.79)
                .engineMean(108.52)
                .pairedDiffMean(23.73)
                .ci95Lower(15.38)
                .ci95Upper(32.08)
                .outcome("Worse")
                .build();

        MetricSummary crimWaitTiered = MetricSummary.builder()
                .metricName("CRIMINAL_OTHER P90 Wait (days)")
                .fcfsMean(79.95)
                .engineMean(78.40)
                .pairedDiffMean(-1.55)
                .ci95Lower(-1.91)
                .ci95Upper(-1.19)
                .outcome("Improved")
                .build();

        MetricSummary dispOverload = MetricSummary.builder()
                .metricName("Mean Time to Disposal (days)")
                .fcfsMean(49.35)
                .engineMean(41.14)
                .pairedDiffMean(-8.21)
                .ci95Lower(-8.46)
                .ci95Upper(-7.95)
                .outcome("Improved")
                .build();

        MetricSummary dispSevere = MetricSummary.builder()
                .metricName("Mean Time to Disposal (days)")
                .fcfsMean(54.96)
                .engineMean(46.33)
                .pairedDiffMean(-8.63)
                .ci95Lower(-9.08)
                .ci95Upper(-8.17)
                .outcome("Improved")
                .build();

        SimulationRunnerService.ScenarioExecutionResult balancedRes = new SimulationRunnerService.ScenarioExecutionResult(
                "balanced", "Balanced", 1.0, 1.0, 20.0, 0.20, List.of(),
                List.of(statDelayRR),
                List.of(statDelayTiered, civilWaitTiered, crimWaitTiered),
                Map.of(), Map.of(), true
        );

        SimulationRunnerService.ScenarioExecutionResult overloadedRes = new SimulationRunnerService.ScenarioExecutionResult(
                "overloaded", "Overloaded", 1.0, 1.30, 20.0, 0.26, List.of(),
                List.of(dispOverload),
                List.of(),
                Map.of(), Map.of(), true
        );

        SimulationRunnerService.ScenarioExecutionResult severeRes = new SimulationRunnerService.ScenarioExecutionResult(
                "severe_overload", "Severe Overload", 1.0, 1.60, 20.0, 0.32, List.of(),
                List.of(dispSevere),
                List.of(),
                Map.of(), Map.of(), true
        );

        File outputDir = tempDir.toFile();
        generator.generateReports(outputDir, config, seeds, 5000L, List.of(balancedRes, overloadedRes, severeRes), List.of());

        File mdFile = new File(outputDir, "simulation-summary.md");
        File jsonFile = new File(outputDir, "simulation-report.json");

        assertTrue(mdFile.exists(), "Markdown summary must exist");
        assertTrue(jsonFile.exists(), "JSON report must exist");

        String mdContent = Files.readString(mdFile.toPath());
        JsonNode jsonNode = objectMapper.readTree(jsonFile);

        // 1. Assert Key Findings in Markdown extracts dynamic numbers with explicit baselines
        assertTrue(mdContent.contains("-11.71 days vs FCFS-RR at load 1.0"), "Markdown must contain dynamic RR delta");
        assertTrue(mdContent.contains("+0.36 days vs FCFS-tiered under engine at load 1.0"), "Markdown must contain dynamic Tiered delta");
        assertTrue(mdContent.contains("-8.21 days vs FCFS-RR at load 1.3"), "Markdown must contain dynamic overload delta");
        assertTrue(mdContent.contains("-8.63 days vs FCFS-RR at load 1.6"), "Markdown must contain dynamic severe delta");
        assertTrue(mdContent.contains("+23.73 days vs FCFS-tiered P90 wait at load 1.0"), "Markdown must contain dynamic civil delta");
        assertTrue(mdContent.contains("-1.55 days vs FCFS-tiered P90 wait at load 1.0"), "Markdown must contain dynamic criminal delta");

        // 2. Cross-verify against JSON node numbers
        JsonNode balancedJson = null;
        for (JsonNode sn : jsonNode.get("scenarios")) {
            if ("balanced".equals(sn.get("scenarioKey").asText())) {
                balancedJson = sn;
                break;
            }
        }
        assertNotNull(balancedJson, "Balanced scenario must be present in JSON");

        // Check statutory delay in JSON matches
        boolean foundStatJson = false;
        for (JsonNode m : balancedJson.get("headlineMetricSummaries")) {
            if ("Arrivals Statutory Mean Delay (days)".equals(m.get("metricName").asText())) {
                assertEquals(-11.71, m.get("pairedDiffMean").asDouble(), 1e-4);
                foundStatJson = true;
                break;
            }
        }
        assertTrue(foundStatJson, "Statutory delay must be in JSON headline metric summaries");
    }

    @Test
    @DisplayName("Validation fails immediately on unknown outcome labels such as 'Better'")
    void testValidateOutcomeThrowsOnUnknownLabel() {
        // Valid outcomes must pass without exception
        assertDoesNotThrow(() -> SimulationReportGenerator.validateOutcome("Improved"));
        assertDoesNotThrow(() -> SimulationReportGenerator.validateOutcome("Worse"));
        assertDoesNotThrow(() -> SimulationReportGenerator.validateOutcome("No significant difference"));
        assertDoesNotThrow(() -> SimulationReportGenerator.validateOutcome("PASS (parity within ±5.0)"));
        assertDoesNotThrow(() -> SimulationReportGenerator.validateOutcome("FAIL (drift > ±5.0)"));

        // Invalid outcomes (including 'Better' or 'Neutral' or null) must fail
        assertThrows(IllegalArgumentException.class, () -> SimulationReportGenerator.validateOutcome("Better"));
        assertThrows(IllegalArgumentException.class, () -> SimulationReportGenerator.validateOutcome("Degraded"));
        assertThrows(IllegalArgumentException.class, () -> SimulationReportGenerator.validateOutcome("Neutral"));
        assertThrows(IllegalArgumentException.class, () -> SimulationReportGenerator.validateOutcome(null));
    }
}
