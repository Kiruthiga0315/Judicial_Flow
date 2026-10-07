package com.judicialflow.common.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persisted record of a single priority-score computation for a case.
 *
 * <p>Each call to the scoring engine appends a <em>new</em> row, making the
 * table a queryable audit trail of how a case's urgency has evolved over time.
 * The most recent row (by {@code computedAt}) is what the REST API surfaces by
 * default.
 *
 * <h2>Column mapping to scoring factors</h2>
 * <table>
 *   <tr><th>Column</th><th>Scoring factor</th></tr>
 *   <tr><td>base_weight</td><td>Case-type statutory urgency contribution</td></tr>
 *   <tr><td>age_multiplier</td><td>Time-pending (aging) contribution</td></tr>
 *   <tr><td>adjournment_boost</td><td>Prior-adjournment contribution</td></tr>
 *   <tr><td>linked_case_bonus</td><td>Linked-case bonus contribution</td></tr>
 *   <tr><td>explanation</td><td>JSON-serialised {@code List<ScoreFactorBreakdown>} for full
 *                              explainability (stored verbatim so historical records are
 *                              self-documenting)</td></tr>
 * </table>
 */
@Entity
@Table(name = "priority_scores",
        indexes = {
                @Index(name = "idx_priority_scores_case_computed",
                        columnList = "case_id, computed_at DESC"),
                @Index(name = "idx_priority_scores_total_score",
                        columnList = "total_score DESC")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriorityScore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The case this score belongs to.
     * ManyToOne because multiple score rows can exist for the same case
     * (score history). Phase 3 removes the OneToOne constraint.
     */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Case legalCase;

    /** Weighted sum of all factor contributions. */
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal totalScore;

    /**
     * Case-type statutory urgency contribution.
     * (Legacy column name kept from V1 schema for backwards compatibility.)
     */
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal baseWeight;

    /**
     * Time-pending (aging) contribution.
     * (Legacy column name kept from V1 schema for backwards compatibility.)
     */
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal ageMultiplier;

    /** Prior-adjournment penalty contribution. */
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal adjournmentBoost;

    /** Linked-case bonus contribution (added in V3 migration). */
    @Builder.Default
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal linkedCaseBonus = BigDecimal.ZERO;

    /** Statutory deadline proximity bonus (added in V10 migration). */
    @Builder.Default
    @Column(name = "statutory_deadline_bonus", nullable = false, precision = 10, scale = 4)
    private BigDecimal statutoryDeadlineBonus = BigDecimal.ZERO;

    /**
     * JSON-serialised {@code List<ScoreFactorBreakdown>} — the full explainability
     * payload.  Stored as TEXT so historical records are self-documenting and the
     * dashboard can reconstruct the breakdown without re-running the engine.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "triggered_by", length = 50)
    @Builder.Default
    private String triggeredBy = "BATCH";

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime computedAt;
}
