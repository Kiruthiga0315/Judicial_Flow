package com.judicialflow.priority.repository;

import com.judicialflow.common.models.PriorityScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for persisted {@link PriorityScore} records.
 *
 * <p>Each compute run appends a new row (score history is intentionally
 * cumulative). The {@code findLatestByCaseId} query retrieves the most
 * recently computed row for a given case, which is what the REST endpoints
 * surface.
 */
@Repository
public interface PriorityScoreRepository extends JpaRepository<PriorityScore, UUID> {

    /**
     * Returns the most recent score record for the given case, or empty
     * if the case has never been scored.
     */
    @Query("SELECT ps FROM PriorityScore ps WHERE ps.legalCase.id = :caseId ORDER BY ps.computedAt DESC LIMIT 1")
    Optional<PriorityScore> findLatestByCaseId(@Param("caseId") UUID caseId);

    /**
     * Returns all score records for a case, ordered newest-first.
     * Useful for auditing score history.
     */
    @Query("SELECT ps FROM PriorityScore ps WHERE ps.legalCase.id = :caseId ORDER BY ps.computedAt DESC")
    List<PriorityScore> findAllByCaseIdOrderByComputedAtDesc(@Param("caseId") UUID caseId);

    /**
     * Returns the top-N most recently computed scores, ranked by
     * {@code totalScore} descending. Only one row per case is considered
     * (the latest computation) so the list won't contain duplicates.
     *
     * <p>Implementation note: uses a subquery to isolate the latest
     * {@code computedAt} per case, then joins back to get the full row.
     */
    @Query("""
            SELECT ps FROM PriorityScore ps
            WHERE ps.computedAt = (
                SELECT MAX(ps2.computedAt)
                FROM PriorityScore ps2
                WHERE ps2.legalCase.id = ps.legalCase.id
            )
            ORDER BY ps.totalScore DESC
            LIMIT :limit
            """)
    List<PriorityScore> findTopNLatestScores(@Param("limit") int limit);

    @org.springframework.data.jpa.repository.Modifying
    @Query("""
            DELETE FROM PriorityScore ps
            WHERE ps.computedAt < :cutoff
              AND ps.id NOT IN (
                  SELECT ps2.id FROM PriorityScore ps2
                  WHERE ps2.computedAt = (
                      SELECT MAX(ps3.computedAt) FROM PriorityScore ps3 WHERE ps3.legalCase.id = ps2.legalCase.id
                  )
              )
            """)
    int pruneScoresOlderThanExceptLatest(@Param("cutoff") java.time.LocalDateTime cutoff);
}
