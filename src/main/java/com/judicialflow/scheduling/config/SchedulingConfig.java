package com.judicialflow.scheduling.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the Phase 4 scheduling engine.
 *
 * <p>All values are documented in {@code application.yml} under the
 * {@code scheduling:} key so they can be reviewed and tuned without
 * reading Java code.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "scheduling")
public class SchedulingConfig {

    /** Default hearing duration in minutes if not specified per-case. */
    private int defaultHearingDurationMinutes = 60;

    /** Number of business days forward to generate schedule proposals. */
    private int horizonDays = 5;

    /** Maximum iterations for the local-search repair phase. */
    private int repairMaxIterations = 100;

    /** Soft constraint weights (sum should be 1.0 for interpretability). */
    private SoftWeights softWeights = new SoftWeights();

    /** Nightly rescheduling batch job settings. */
    private NightlyJob nightlyJob = new NightlyJob();

    @Data
    public static class SoftWeights {
        /** Weight for priority ordering: higher-priority cases get earlier slots. */
        private double priorityOrdering = 0.5;

        /** Weight for workload balance across judges. */
        private double workloadBalance = 0.3;

        /** Weight for minimizing schedule churn versus previous run. */
        private double scheduleChurn = 0.2;
    }

    @Data
    public static class NightlyJob {
        private String cron = "0 0 2 * * ?";
        private boolean enabled = true;
    }
}
