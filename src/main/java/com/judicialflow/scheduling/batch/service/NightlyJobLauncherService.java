package com.judicialflow.scheduling.batch.service;

import com.judicialflow.scheduling.batch.config.NightlyReschedulingBatchConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NightlyJobLauncherService {

    private final JobLauncher jobLauncher;

    @Qualifier("nightlyReschedulingJob")
    private final Job nightlyReschedulingJob;

    /**
     * Launch the nightly batch job with parameters that make each execution safely re-runnable
     * while guaranteeing idempotency.
     *
     * @param triggerType e.g., "MANUAL_ADMIN" or "SCHEDULED_CRON"
     * @return JobExecution details
     */
    public JobExecution launchNightlyReschedulingJob(String triggerType)
            throws JobExecutionAlreadyRunningException, JobRestartException,
            JobInstanceAlreadyCompleteException, JobParametersInvalidException {
        return launchNightlyReschedulingJob(triggerType, null);
    }

    public JobExecution launchNightlyReschedulingJob(String triggerType, Integer horizonDays)
            throws JobExecutionAlreadyRunningException, JobRestartException,
            JobInstanceAlreadyCompleteException, JobParametersInvalidException {

        long timestamp = System.currentTimeMillis();
        String executionKey = UUID.randomUUID().toString();

        JobParametersBuilder builder = new JobParametersBuilder()
                .addLong("timestamp", timestamp)
                .addString("triggerType", triggerType != null ? triggerType : "MANUAL")
                .addString("executionKey", executionKey);

        if (horizonDays != null) {
            builder.addLong("horizonDays", horizonDays.longValue());
        }

        JobParameters params = builder.toJobParameters();

        log.info("Launching job {} with triggerType={}, horizonDays={}, timestamp={}",
                NightlyReschedulingBatchConfig.JOB_NAME, triggerType, horizonDays, timestamp);

        JobExecution execution = jobLauncher.run(nightlyReschedulingJob, params);
        log.info("Job {} completed with status: {}",
                NightlyReschedulingBatchConfig.JOB_NAME, execution.getStatus());

        return execution;
    }
}
