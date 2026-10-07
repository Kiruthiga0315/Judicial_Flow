package com.judicialflow.priority.service;

import com.judicialflow.priority.repository.PriorityScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Priority score retention service.
 *
 * Retention Policy Decision:
 * --------------------------
 * Scores older than priority.retention.days (default: 30 days) are pruned,
 * while ALWAYS retaining the single most recent score for every case.
 * This bounds table size as nightly batches append score history,
 * while ensuring that pending cases never lose their current priority status.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriorityScoreRetentionService {

    private final PriorityScoreRepository priorityScoreRepository;

    @Value("${priority.retention.days:30}")
    private int retentionDays;

    @Transactional
    public int pruneOldScores() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        log.info("Pruning priority scores older than {} (retention period: {} days)...",
                cutoff, retentionDays);
        int deleted = priorityScoreRepository.pruneScoresOlderThanExceptLatest(cutoff);
        log.info("Retention pruning complete. Pruned {} historical priority score records.", deleted);
        return deleted;
    }
}
