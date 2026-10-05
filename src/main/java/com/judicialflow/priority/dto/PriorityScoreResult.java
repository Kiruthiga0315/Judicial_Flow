package com.judicialflow.priority.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The full result of computing a case's priority score.
 *
 * <p>This is the object returned by both:
 * <ul>
 *   <li>{@code GET /api/priority/cases/{caseId}/score} – single-case score</li>
 *   <li>{@code GET /api/priority/cases/top?limit=N} – ranked list of top-N cases</li>
 * </ul>
 *
 * <p>The {@code factors} list is the explainability layer.  Each entry in the
 * list maps to one scoring dimension so the dashboard can render a breakdown
 * table alongside the total score.
 */
@Value
@Builder
@Schema(description = "Priority score result with full factor breakdown for explainability")
public class PriorityScoreResult {

    @Schema(description = "UUID of the scored case", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID caseId;

    @Schema(description = "Human-readable case number", example = "BAIL-2024-00312")
    String caseNumber;

    @Schema(description = "Computed priority score (higher = more urgent)", example = "92.50")
    BigDecimal totalScore;

    @Schema(description = "Ordered list of factor breakdowns, from highest contribution to lowest")
    List<ScoreFactorBreakdown> factors;

    @Schema(description = "Single-sentence summary for the dashboard header",
            example = "Score 92.50: BAIL case pending 182 days with 3 adjournments.")
    String summary;

    @Schema(description = "Timestamp when this score was computed")
    LocalDateTime computedAt;

    @Schema(description = "UUID of the persisted PriorityScore record in the database")
    UUID persistedScoreId;
}
