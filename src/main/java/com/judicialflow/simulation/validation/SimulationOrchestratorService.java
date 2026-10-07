package com.judicialflow.simulation.validation;

import com.judicialflow.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service orchestrating the complete simulation process, managing asynchronous runs,
 * and performing the single audit write per simulation run.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationOrchestratorService {

    private final SimulationConfig simulationConfig;
    private final SimulationRunnerService runnerService;
    private final SimulationStatsCalculator statsCalculator;
    private final SimulationReportGenerator reportGenerator;
    private final AuditService auditService;

    public record SimulationStatus(
            String runId,
            String status, // "PENDING", "RUNNING", "COMPLETED", "FAILED"
            long startTimeMillis,
            long executionTimeMillis,
            String reportJsonPath,
            String reportHtmlPath,
            String summaryMdPath,
            String errorMessage
    ) {}

    private final Map<String, SimulationStatus> runStatusMap = new ConcurrentHashMap<>();
    private volatile String latestRunId;

    public void registerPendingRun(String runId) {
        this.latestRunId = runId;
        runStatusMap.put(runId, new SimulationStatus(
                runId, "RUNNING", System.currentTimeMillis(), 0, null, null, null, null
        ));
    }

    public SimulationStatus runSimulation(boolean quickMode, String actorUsername, String actorRole) {
        return runSimulationCustom(quickMode, false, null, false, actorUsername, actorRole);
    }

    public SimulationStatus runSimulationWithRunId(String runId, boolean quickMode, String actorUsername, String actorRole) {
        return runSimulationWithRunIdCustom(runId, quickMode, false, null, false, actorUsername, actorRole);
    }

    public SimulationStatus runSimulationCustom(
            boolean quickMode,
            boolean noSensitivity,
            Integer sensitivitySeedsCount,
            boolean sensitivityOnly,
            String actorUsername,
            String actorRole
    ) {
        String runId = UUID.randomUUID().toString();
        registerPendingRun(runId);
        return runSimulationWithRunIdCustom(runId, quickMode, noSensitivity, sensitivitySeedsCount, sensitivityOnly, actorUsername, actorRole);
    }

    public SimulationStatus runSimulationWithRunIdCustom(
            String runId,
            boolean quickMode,
            boolean noSensitivity,
            Integer sensitivitySeedsCount,
            boolean sensitivityOnly,
            String actorUsername,
            String actorRole
    ) {
        long startTime = System.currentTimeMillis();
        List<Long> seeds = quickMode ? simulationConfig.getQuickSeeds() : simulationConfig.getDefaultSeeds();
        log.info("Starting simulation run {} (quickMode={}, noSensitivity={}, sensitivityOnly={}, sensitivitySeeds={})",
                runId, quickMode, noSensitivity, sensitivityOnly, sensitivitySeedsCount);

        try {
            LocalDate startDate = LocalDate.of(2026, 1, 5); // Simulated Monday start
            List<SimulationRunnerService.ScenarioExecutionResult> scenarioResults = new ArrayList<>();

            if (!sensitivityOnly) {
                // Calibrated 4 scenarios: 0.7, 1.0, 1.3, 1.6 offered loads
                Map<String, SimulationConfig.ScenarioConfig> scenarios = simulationConfig.getScenarios();
                if (scenarios == null || scenarios.isEmpty()) {
                    scenarios = new LinkedHashMap<>();
                    scenarios.put("moderate", createScenario("Moderate Load (Offered Load ~0.7)", 13.5, 1.0));
                    scenarios.put("balanced", createScenario("Balanced (Offered Load ~1.0)", 19.25, 1.0));
                    scenarios.put("overloaded", createScenario("Overloaded (Offered Load ~1.3)", 25.0, 1.0));
                    scenarios.put("severe_overload", createScenario("Severe Overload (Offered Load ~1.6)", 30.8, 1.0));
                }

                for (Map.Entry<String, SimulationConfig.ScenarioConfig> entry : scenarios.entrySet()) {
                    String key = entry.getKey();
                    SimulationConfig.ScenarioConfig sc = entry.getValue();
                    log.info("Executing simulation scenario: {}", sc.getName());
                    SimulationRunnerService.ScenarioExecutionResult res = runnerService.runScenario(
                            key,
                            sc.getName(),
                            sc.getDailyArrivalRate(),
                            sc.getCapacityMultiplier(),
                            seeds,
                            startDate,
                            statsCalculator
                    );
                    scenarioResults.add(res);
                }
            }

            // Run sensitivity analysis
            List<SimulationRunnerService.SensitivityResult> sensitivityResults = List.of();
            if (!quickMode && !noSensitivity) {
                try {
                    List<Long> sensSeeds = seeds;
                    if (sensitivitySeedsCount != null && sensitivitySeedsCount > 0 && sensitivitySeedsCount < seeds.size()) {
                        sensSeeds = seeds.subList(0, sensitivitySeedsCount);
                    }
                    log.info("Executing sensitivity analysis across balanced and overloaded scenarios (seeds={})...", sensSeeds);
                    sensitivityResults = runnerService.runSensitivityAnalysis(sensSeeds, startDate, statsCalculator, scenarioResults);
                } catch (Exception se) {
                    log.warn("Sensitivity analysis failed or skipped: {}", se.getMessage());
                }
            } else {
                log.info("Skipping sensitivity analysis (quickMode={}, noSensitivity={})", quickMode, noSensitivity);
            }

            long elapsed = System.currentTimeMillis() - startTime;

            // Generate reports in reports/ directory if not sensitivity-only
            File reportsDir = new File("reports");
            if (!sensitivityOnly) {
                reportGenerator.generateReports(reportsDir, simulationConfig, seeds, elapsed, scenarioResults, sensitivityResults);
            }

            File jsonFile = new File(reportsDir, "simulation-report.json");
            File htmlFile = new File(reportsDir, "simulation-report.html");
            File mdFile = new File(reportsDir, "simulation-summary.md");

            // Single audit entry per simulation run
            try {
                auditService.logExplicit(
                        null,
                        actorUsername != null ? actorUsername : "ADMIN",
                        actorRole != null ? actorRole : "ADMIN",
                        "SIMULATION_RUN",
                        runId,
                        "EXECUTE_SIMULATION",
                        "VALIDATION_BENCHMARK",
                        Map.of("quickMode", quickMode, "seedCount", seeds.size(), "horizonDays", simulationConfig.getHorizonDays()),
                        Map.of("status", "COMPLETED", "executionTimeMs", elapsed, "scenariosCount", scenarioResults.size()),
                        "Simulation execution completed across " + scenarioResults.size() + " calibrated scenarios."
                );
            } catch (Exception e) {
                log.warn("Could not write audit log for simulation run: {}", e.getMessage());
            }

            SimulationStatus completedStatus = new SimulationStatus(
                    runId,
                    "COMPLETED",
                    startTime,
                    elapsed,
                    jsonFile.getAbsolutePath(),
                    htmlFile.getAbsolutePath(),
                    mdFile.getAbsolutePath(),
                    null
            );
            runStatusMap.put(runId, completedStatus);
            return completedStatus;

        } catch (Exception e) {
            log.error("Simulation run {} failed", runId, e);
            long elapsed = System.currentTimeMillis() - startTime;
            SimulationStatus failedStatus = new SimulationStatus(
                    runId,
                    "FAILED",
                    startTime,
                    elapsed,
                    null,
                    null,
                    null,
                    e.getMessage()
            );
            runStatusMap.put(runId, failedStatus);
            return failedStatus;
        }
    }

    private SimulationConfig.ScenarioConfig createScenario(String name, double arrivalRate, double mult) {
        SimulationConfig.ScenarioConfig sc = new SimulationConfig.ScenarioConfig();
        sc.setName(name);
        sc.setDailyArrivalRate(arrivalRate);
        sc.setCapacityMultiplier(mult);
        return sc;
    }

    public Optional<SimulationStatus> getStatus(String runId) {
        return Optional.ofNullable(runStatusMap.get(runId));
    }

    public Optional<SimulationStatus> getLatestStatus() {
        if (latestRunId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(runStatusMap.get(latestRunId));
    }
}
