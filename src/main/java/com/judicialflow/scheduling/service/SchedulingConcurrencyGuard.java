package com.judicialflow.scheduling.service;

import com.judicialflow.scheduling.model.RunStatus;
import com.judicialflow.scheduling.model.SchedulingRun;
import com.judicialflow.scheduling.repository.SchedulingRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Concurrency guard ensuring only one scheduling run or batch rescheduling job
 * executes at any given time across all threads.
 *
 * <p><b>Architecture & Deployment Scope: Single-Instance Monolith Guard</b>
 * <ul>
 *   <li><b>In-Memory JVM Layer:</b> Uses a {@link ReentrantLock} with volatile {@code activeRunId} tracking
 *       to provide zero-latency, sub-millisecond mutual exclusion between concurrent web requests
 *       (e.g., manual admin trigger vs. background Quartz/Spring Batch cron) within the same JVM instance.</li>
 *   <li><b>Database State Guard:</b> Complemented by a persistent {@code RUNNING} state query on the
 *       {@code scheduling_runs} table, along with an automatic 15-minute stale-run watchdog that recovers
 *       from JVM crashes or killed containers.</li>
 *   <li><b>Horizontal Scaling Roadmap:</b> If JudicialFlow is scaled out to multiple stateless replicas,
 *       this single-instance guard should be transitioned to PostgreSQL advisory locks
 *       ({@code SELECT pg_try_advisory_lock(hashtext('judicialflow_scheduling_guard'))})
 *       or ShedLock/distributed Redis locks to guarantee cluster-wide mutual exclusion at the database engine level.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SchedulingConcurrencyGuard {

    private final SchedulingRunRepository runRepository;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile UUID activeRunId = null;

    /**
     * Try to acquire the execution guard.
     *
     * @param prospectiveRunId ID of the run about to start
     * @return Optional containing the active running run ID if already in progress,
     *         or empty Optional if successfully acquired.
     */
    public synchronized Optional<UUID> tryAcquire(UUID prospectiveRunId) {
        // Clean up stale DB runs if any (e.g. from previous server crash)
        recoverStaleDbRuns();

        // If current thread already holds the lock, this is re-entrant execution
        // (e.g. AdminBatchJobController / Scheduler calling batch job which invokes SchedulingService)
        if (lock.isHeldByCurrentThread()) {
            lock.tryLock();
            return Optional.empty();
        }

        if (this.activeRunId != null && !this.activeRunId.equals(prospectiveRunId)) {
            return Optional.of(this.activeRunId);
        }

        UUID dbRunningId = lookupRunningRunId();
        if (dbRunningId != null) {
            return Optional.of(dbRunningId);
        }

        if (!lock.tryLock()) {
            return Optional.of(this.activeRunId != null ? this.activeRunId : prospectiveRunId);
        }

        // Lock acquired
        this.activeRunId = prospectiveRunId;
        return Optional.empty();
    }

    public synchronized void setActiveRunId(UUID runId) {
        this.activeRunId = runId;
    }

    public synchronized UUID getActiveRunId() {
        return activeRunId != null ? activeRunId : lookupRunningRunId();
    }

    public synchronized void release() {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            if (lock.getHoldCount() == 0) {
                this.activeRunId = null;
            }
        } else {
            this.activeRunId = null;
        }
    }

    private UUID lookupRunningRunId() {
        return runRepository.findTopByStatusOrderByTriggeredAtDesc(RunStatus.RUNNING)
                .map(SchedulingRun::getId)
                .orElse(null);
    }

    private void recoverStaleDbRuns() {
        try {
            Optional<SchedulingRun> running = runRepository.findTopByStatusOrderByTriggeredAtDesc(RunStatus.RUNNING);
            if (running.isPresent()) {
                SchedulingRun r = running.get();
                if (r.getTriggeredAt() != null && r.getTriggeredAt().isBefore(LocalDateTime.now().minusMinutes(15))) {
                    log.warn("Stale RUNNING run {} detected (started at {}). Marking as FAILED.",
                            r.getId(), r.getTriggeredAt());
                    r.setStatus(RunStatus.FAILED);
                    r.setCompletedAt(LocalDateTime.now());
                    r.setErrorMessage("Timed out / crashed");
                    runRepository.save(r);
                }
            }
        } catch (Exception e) {
            log.warn("Error checking stale runs", e);
        }
    }
}
