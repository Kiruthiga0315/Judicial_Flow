package com.judicialflow.scheduling.engine;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a candidate hearing slot: a specific (judge, courtroom, startTime) triple
 * that a case could potentially be assigned to.
 *
 * <p>The {@code softScore} field is populated during soft-constraint evaluation;
 * lower scores are preferred (the score represents a penalty).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateSlot {
    private UUID judgeId;
    private String judgeName;
    private UUID courtroomId;
    private String courtroomName;
    private LocalDateTime startTime;
    private int durationMinutes;

    /** Combined weighted soft-constraint penalty. Lower = better. */
    @Builder.Default
    private BigDecimal softScore = BigDecimal.ZERO;

    /** Human-readable explanation of how the soft score was computed. */
    private String scoreBreakdown;
}
