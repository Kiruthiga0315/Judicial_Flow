package com.judicialflow.simulation.validation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs simulation directly when passed CLI argument `--simulation` (and optional `--quick`).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SimulationCommandLineRunner implements ApplicationRunner {

    private final SimulationOrchestratorService orchestratorService;

    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("simulation")) {
            boolean quick = args.containsOption("quick");
            boolean noSensitivity = args.containsOption("no-sensitivity") || args.containsOption("skip-sensitivity");
            boolean sensitivityOnly = args.containsOption("sensitivity-only");

            Integer sensitivitySeedsCount = null;
            if (args.containsOption("sensitivity-seeds")) {
                try {
                    sensitivitySeedsCount = Integer.parseInt(args.getOptionValues("sensitivity-seeds").get(0));
                } catch (Exception ignored) {}
            }

            log.info("CLI Argument '--simulation' detected (quickMode={}, noSensitivity={}, sensitivityOnly={}, sensitivitySeeds={}). Executing simulation...",
                    quick, noSensitivity, sensitivityOnly, sensitivitySeedsCount);

            SimulationOrchestratorService.SimulationStatus status = orchestratorService.runSimulationCustom(
                    quick, noSensitivity, sensitivitySeedsCount, sensitivityOnly, "SYSTEM", "SYSTEM"
            );

            log.info("CLI Simulation finished with status: {} in {} ms. Report JSON: {}",
                    status.status(), status.executionTimeMillis(), status.reportJsonPath());

            System.out.println("================================================================================");
            System.out.println("  JUDICIALFLOW SIMULATION BENCHMARK COMPLETE (Status: " + status.status() + ")");
            System.out.printf("  Execution Time: %.2f seconds%n", status.executionTimeMillis() / 1000.0);
            System.out.println("  Reports written to:");
            System.out.println("   - " + status.reportJsonPath());
            System.out.println("   - " + status.reportHtmlPath());
            System.out.println("   - " + status.summaryMdPath());
            System.out.println("================================================================================");

            System.exit(status.status().equals("COMPLETED") ? 0 : 1);
        }
    }
}
