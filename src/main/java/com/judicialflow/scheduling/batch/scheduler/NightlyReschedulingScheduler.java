package com.judicialflow.scheduling.batch.scheduler;

import com.judicialflow.scheduling.batch.service.NightlyJobLauncherService;
import com.judicialflow.scheduling.config.SchedulingConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NightlyReschedulingScheduler {

    private final NightlyJobLauncherService jobLauncherService;
    private final SchedulingConfig schedulingConfig;
    private final com.judicialflow.scheduling.service.SchedulingConcurrencyGuard concurrencyGuard;

    /**
     * Nightly scheduled execution of the rescheduling batch job.
     * Evaluated using the configured cron expression (default: 2 AM nightly).
     */
    @Scheduled(cron = "${scheduling.nightly-job.cron:0 0 2 * * ?}")
    public void runScheduledNightlyJob() {
        if (!schedulingConfig.getNightlyJob().isEnabled()) {
            log.info("Nightly rescheduling job scheduler is disabled via configuration. Skipping.");
            return;
        }

        java.util.UUID prospectiveRunId = java.util.UUID.randomUUID();
        java.util.Optional<java.util.UUID> running = concurrencyGuard.tryAcquire(prospectiveRunId);
        if (running.isPresent()) {
            log.warn("Nightly rescheduling cron skipped: another scheduling run is already in progress (runId={})", running.get());
            return;
        }

        log.info("Nightly scheduled trigger fired. Starting nightly rescheduling batch job...");
        try {
            jobLauncherService.launchNightlyReschedulingJob("SCHEDULED_CRON");
        } catch (Exception e) {
            log.error("Failed to execute scheduled nightly rescheduling job", e);
        } finally {
            concurrencyGuard.release();
        }
    }
}
