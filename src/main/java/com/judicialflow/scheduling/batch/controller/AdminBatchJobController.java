package com.judicialflow.scheduling.batch.controller;

import com.judicialflow.scheduling.batch.dto.BatchJobTriggerResponse;
import com.judicialflow.scheduling.batch.dto.ScheduleDiffSummary;
import com.judicialflow.scheduling.batch.service.NightlyJobLauncherService;
import com.judicialflow.scheduling.batch.service.ScheduleDiffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/batch")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Batch Operations", description = "Phase 6: Nightly batch rescheduling trigger & management")
public class AdminBatchJobController {

    private final NightlyJobLauncherService jobLauncherService;
    private final ScheduleDiffService diffService;
    private final com.judicialflow.scheduling.service.SchedulingConcurrencyGuard concurrencyGuard;
    private final com.judicialflow.scheduling.repository.SchedulingRunRepository runRepository;

    @PostMapping("/reschedule")
    @Operation(summary = "Manually trigger the nightly rescheduling batch job",
            description = "Triggers the Spring Batch job that recomputes priority scores for all open cases, " +
                    "re-runs the scheduling engine to produce a fresh proposed schedule, and computes the diff against " +
                    "the previous run.")
    public ResponseEntity<BatchJobTriggerResponse> triggerReschedulingBatch(
            @RequestParam(required = false, defaultValue = "MANUAL_ADMIN") String triggerSource,
            @RequestParam(required = false, defaultValue = "false") boolean async) throws Exception {

        UUID prospectiveRunId = UUID.randomUUID();

        if (async) {
            UUID running = concurrencyGuard.getActiveRunId();
            if (running != null) {
                throw new com.judicialflow.scheduling.exception.SchedulingConflictException(running);
            }

            concurrencyGuard.setActiveRunId(prospectiveRunId);
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    log.info("Async batch rescheduling job starting for prospectiveRunId={} (triggerSource={})",
                            prospectiveRunId, triggerSource);
                    Optional<UUID> lockAcquired = concurrencyGuard.tryAcquire(prospectiveRunId);
                    jobLauncherService.launchNightlyReschedulingJob(triggerSource);
                } catch (Exception e) {
                    log.error("Async batch rescheduling job failed", e);
                } finally {
                    concurrencyGuard.release();
                }
            });

            BatchJobTriggerResponse asyncResponse = BatchJobTriggerResponse.builder()
                    .jobName("nightlyReschedulingJob")
                    .status("ACCEPTED")
                    .runId(prospectiveRunId.toString())
                    .startTime(LocalDateTime.now())
                    .build();

            return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED).body(asyncResponse);
        }

        Optional<UUID> runningRun = concurrencyGuard.tryAcquire(prospectiveRunId);
        if (runningRun.isPresent()) {
            throw new com.judicialflow.scheduling.exception.SchedulingConflictException(runningRun.get());
        }

        JobExecution execution;
        try {
            log.info("Admin manual trigger received for rescheduling batch job (triggerSource={})", triggerSource);
            execution = jobLauncherService.launchNightlyReschedulingJob(triggerSource);
        } finally {
            concurrencyGuard.release();
        }

        String runIdStr = execution.getExecutionContext().getString("runId", null);
        Integer recomputedCasesCount = execution.getExecutionContext().containsKey("recomputedCasesCount")
                ? execution.getExecutionContext().getInt("recomputedCasesCount") : null;
        Integer assignedCount = execution.getExecutionContext().containsKey("assignedCount")
                ? execution.getExecutionContext().getInt("assignedCount") : null;

        ScheduleDiffSummary diffSummary = null;
        if (runIdStr != null) {
            try {
                diffSummary = diffService.computeRunDiff(UUID.fromString(runIdStr));
            } catch (Exception e) {
                log.warn("Could not compute diff summary for triggered run {}", runIdStr, e);
            }
        }

        LocalDateTime startTime = execution.getStartTime();
        LocalDateTime endTime = execution.getEndTime();

        BatchJobTriggerResponse response = BatchJobTriggerResponse.builder()
                .jobExecutionId(execution.getId())
                .jobName(execution.getJobInstance().getJobName())
                .status(execution.getStatus().name())
                .exitCode(execution.getExitStatus().getExitCode())
                .startTime(startTime)
                .endTime(endTime)
                .runId(runIdStr)
                .recomputedCasesCount(recomputedCasesCount)
                .assignedCount(assignedCount)
                .diffSummary(diffSummary)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{runId}")
    @Operation(summary = "Get execution status of a batch rescheduling run",
            description = "Returns current status, execution details, and diff summary for an asynchronous batch run.")
    public ResponseEntity<BatchJobTriggerResponse> getRunStatus(@PathVariable UUID runId) {
        log.info("Fetching status for batch scheduling run {}", runId);
        Optional<com.judicialflow.scheduling.model.SchedulingRun> runOpt = runRepository.findById(runId);
        if (runOpt.isEmpty()) {
            if (concurrencyGuard.getActiveRunId() != null && concurrencyGuard.getActiveRunId().equals(runId)) {
                return ResponseEntity.ok(BatchJobTriggerResponse.builder()
                        .jobName("nightlyReschedulingJob")
                        .runId(runId.toString())
                        .status("RUNNING")
                        .build());
            }
            return ResponseEntity.notFound().build();
        }

        com.judicialflow.scheduling.model.SchedulingRun run = runOpt.get();
        ScheduleDiffSummary diffSummary = null;
        if (run.getStatus() == com.judicialflow.scheduling.model.RunStatus.COMPLETED) {
            try {
                diffSummary = diffService.computeRunDiff(run.getId());
            } catch (Exception e) {
                log.warn("Could not compute diff summary for run {}", run.getId(), e);
            }
        }

        BatchJobTriggerResponse response = BatchJobTriggerResponse.builder()
                .jobName("nightlyReschedulingJob")
                .runId(run.getId().toString())
                .status(run.getStatus().name())
                .startTime(run.getTriggeredAt())
                .endTime(run.getCompletedAt())
                .assignedCount(run.getTotalAssigned())
                .diffSummary(diffSummary)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/diff/{runId}")
    @Operation(summary = "Get the schedule and priority score diff for a scheduling run",
            description = "Computes and returns the diff of assignments and priority score changes between the specified run and its preceding run.")
    public ResponseEntity<ScheduleDiffSummary> getRunDiff(@PathVariable UUID runId) {
        log.info("Fetching diff for scheduling run {}", runId);
        ScheduleDiffSummary summary = diffService.computeRunDiff(runId);
        return ResponseEntity.ok(summary);
    }
}
