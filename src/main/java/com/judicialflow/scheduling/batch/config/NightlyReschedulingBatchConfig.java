package com.judicialflow.scheduling.batch.config;

import com.judicialflow.priority.dto.PriorityScoreResult;
import com.judicialflow.priority.service.PriorityScoreService;
import com.judicialflow.scheduling.batch.dto.ScheduleDiffSummary;
import com.judicialflow.scheduling.batch.service.ScheduleDiffService;
import com.judicialflow.scheduling.dto.SchedulingRunResponse;
import com.judicialflow.scheduling.service.SchedulingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class NightlyReschedulingBatchConfig {

    public static final String JOB_NAME = "nightlyReschedulingJob";
    public static final String STEP_PRIORITY_SCORING = "recomputePriorityScoresStep";
    public static final String STEP_RESCHEDULING = "rerunSchedulingEngineStep";
    public static final String STEP_DIFF_LOGGING = "diffAndLogScheduleStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final PriorityScoreService priorityScoreService;
    private final SchedulingService schedulingService;
    private final ScheduleDiffService scheduleDiffService;
    private final com.judicialflow.priority.service.PriorityScoreRetentionService retentionService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Bean(name = "nightlyReschedulingJob")
    public Job nightlyReschedulingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(recomputePriorityScoresStep())
                .next(rerunSchedulingEngineStep())
                .next(diffAndLogScheduleStep())
                .build();
    }

    @Bean
    public Step recomputePriorityScoresStep() {
        return new StepBuilder(STEP_PRIORITY_SCORING, jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Batch Step 1: Recomputing priority scores for all open cases...");
                    List<PriorityScoreResult> results = priorityScoreService.computeAllOpenCases("NIGHTLY_BATCH");
                    log.info("Batch Step 1 complete: Recomputed and persisted priority scores for {} cases.", results.size());
                    chunkContext.getStepContext().getStepExecution().getJobExecution()
                            .getExecutionContext().putInt("recomputedCasesCount", results.size());

                    // Fix 5: Apply retention policy
                    try {
                        retentionService.pruneOldScores();
                    } catch (Exception e) {
                        log.warn("Non-fatal: failed to prune old priority scores during nightly batch", e);
                    }

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step rerunSchedulingEngineStep() {
        return new StepBuilder(STEP_RESCHEDULING, jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Batch Step 2: Re-running scheduling engine to produce fresh proposed schedule...");
                    Long horizonDaysParam = chunkContext.getStepContext().getStepExecution().getJobExecution()
                            .getJobParameters().getLong("horizonDays");
                    com.judicialflow.scheduling.dto.SchedulingConfigDto configDto = horizonDaysParam != null
                            ? com.judicialflow.scheduling.dto.SchedulingConfigDto.builder().horizonDays(horizonDaysParam.intValue()).build()
                            : null;
                    SchedulingRunResponse runResponse = schedulingService.triggerSchedulingRun(configDto);
                    log.info("Batch Step 2 complete: Generated run id={}, assigned={}, unschedulable={}",
                            runResponse.getRunId(), runResponse.getTotalAssigned(), runResponse.getTotalUnschedulable());
                    chunkContext.getStepContext().getStepExecution().getJobExecution()
                            .getExecutionContext().putString("runId", runResponse.getRunId().toString());
                    chunkContext.getStepContext().getStepExecution().getJobExecution()
                            .getExecutionContext().putInt("assignedCount", runResponse.getTotalAssigned());
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step diffAndLogScheduleStep() {
        return new StepBuilder(STEP_DIFF_LOGGING, jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("Batch Step 3: Computing schedule diff against previous run...");
                    String runIdStr = chunkContext.getStepContext().getStepExecution().getJobExecution()
                            .getExecutionContext().getString("runId");
                    if (runIdStr != null) {
                        java.util.UUID currentRunId = java.util.UUID.fromString(runIdStr);
                        ScheduleDiffSummary diff = scheduleDiffService.computeRunDiff(currentRunId);
                        chunkContext.getStepContext().getStepExecution().getJobExecution()
                                .getExecutionContext().putInt("newAssignmentsCount", diff.getNewAssignmentsCount());
                        chunkContext.getStepContext().getStepExecution().getJobExecution()
                                .getExecutionContext().putInt("modifiedAssignmentsCount", diff.getModifiedAssignmentsCount());
                        chunkContext.getStepContext().getStepExecution().getJobExecution()
                                .getExecutionContext().putInt("reprioritizedCasesCount", diff.getReprioritizedCasesCount());
                        
                        // Publish event for notifications (reprioritization digest)
                        eventPublisher.publishEvent(new com.judicialflow.scheduling.event.NightlyRunCompletedEvent(
                                this, currentRunId, diff.getReprioritizations()));
                    } else {
                        log.warn("RunId not found in JobExecutionContext. Skipping diff step.");
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
