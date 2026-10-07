package com.judicialflow.priority.service;

import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.models.Case;
import com.judicialflow.priority.config.PriorityWeightsConfig;
import com.judicialflow.priority.dto.PriorityScoreResult;
import com.judicialflow.priority.dto.ScoreFactorBreakdown;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stateless calculator that turns a {@link Case} entity into an explainable
 * {@link PriorityScoreResult}.
 *
 * <h2>Scoring formula</h2>
 * <pre>
 *   totalScore = caseTypeUrgency(type)
 *              + min(daysPending × agingWeightPerDay, maxAgingContribution)
 *              + min(priorAdjournments × adjournmentBoostPerOccurrence, maxAdjournmentContribution)
 *              + (hasLinkedCase ? linkedCaseBonus : 0)
 * </pre>
 *
 * <h2>Explainability</h2>
 * Every call returns a {@link PriorityScoreResult} that contains:
 * <ol>
 *   <li>The total score.</li>
 *   <li>A {@link ScoreFactorBreakdown} for <em>each</em> factor: raw value,
 *       weight used, and exact points contributed — exactly what the registrar
 *       dashboard needs to render a breakdown table.</li>
 *   <li>A one-sentence {@code summary} for the dashboard header.</li>
 * </ol>
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li>The calculator is a Spring {@code @Component} (not a service) because
 *       it carries no state and performs no I/O.  The owning
 *       {@link PriorityScoreService} handles DB persistence and transactions.</li>
 *   <li>All arithmetic is done with {@link BigDecimal} at 4 decimal places to
 *       avoid floating-point drift in persisted scores.</li>
 *   <li>Caps on aging and adjournment contributions are intentional: they
 *       prevent very old or heavily-adjourned cases from monopolising the top
 *       of the list at the expense of genuinely urgent new cases.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PriorityScoreCalculator {

    private static final int SCALE = 4;
    private static final RoundingMode RM = RoundingMode.HALF_UP;

    private final PriorityWeightsConfig weights;

    /**
     * Compute the priority score for a case.
     *
     * <p>The {@code persistedScoreId} field in the returned result is always
     * {@code null} here; the caller ({@link PriorityScoreService}) fills it in
     * after persisting the record.
     *
     * @param legalCase the case to score (must not be null; soft-deleted cases are
     *              accepted — callers decide whether to filter them out)
     * @return a fully populated {@link PriorityScoreResult} with breakdown
     */
    public PriorityScoreResult calculate(Case legalCase) {
        return calculate(legalCase, LocalDate.now());
    }

    public PriorityScoreResult calculate(Case legalCase, LocalDate asOfDate) {
        log.debug("Calculating priority score for case {} as of {}", legalCase.getCaseNumber(), asOfDate);

        LocalDate today = asOfDate != null ? asOfDate : LocalDate.now();
        List<ScoreFactorBreakdown> factors = new ArrayList<>();

        // -----------------------------------------------------------------------
        // Factor 1: Case-type statutory urgency
        // -----------------------------------------------------------------------
        BigDecimal typeUrgency = weights.getCaseTypeUrgency()
                .getOrDefault(legalCase.getCaseType(), BigDecimal.ZERO)
                .setScale(SCALE, RM);

        factors.add(ScoreFactorBreakdown.builder()
                .factorName("Case Type Urgency")
                .rawValue(BigDecimal.ONE.setScale(SCALE, RM))   // 1 occurrence × weight
                .weight(typeUrgency)
                .contribution(typeUrgency)
                .explanation(buildTypeExplanation(legalCase.getCaseType(), typeUrgency))
                .build());

        // -----------------------------------------------------------------------
        // Factor 2: Time pending (aging)
        // -----------------------------------------------------------------------
        long daysPending = ChronoUnit.DAYS.between(legalCase.getFilingDate(), today);
        // daysPending can be 0 on filing day — guard against negative values
        // (future filing dates are blocked by the API but be defensive here)
        long safeDays = Math.max(0L, daysPending);
        BigDecimal rawAgingContribution = BigDecimal.valueOf(safeDays)
                .multiply(weights.getAgingWeightPerDay())
                .setScale(SCALE, RM);
        BigDecimal agingContribution = rawAgingContribution.min(weights.getMaxAgingContribution())
                .setScale(SCALE, RM);
        boolean agingCapped = rawAgingContribution.compareTo(weights.getMaxAgingContribution()) > 0;

        factors.add(ScoreFactorBreakdown.builder()
                .factorName("Time Pending (Aging)")
                .rawValue(BigDecimal.valueOf(safeDays).setScale(SCALE, RM))
                .weight(weights.getAgingWeightPerDay())
                .contribution(agingContribution)
                .explanation(buildAgingExplanation(safeDays, weights.getAgingWeightPerDay(),
                        agingContribution, weights.getMaxAgingContribution(), agingCapped))
                .build());

        // -----------------------------------------------------------------------
        // Factor 3: Prior adjournments
        // -----------------------------------------------------------------------
        int adjournments = legalCase.getPriorAdjournments();
        BigDecimal rawAdjournmentContribution = BigDecimal.valueOf(adjournments)
                .multiply(weights.getAdjournmentBoostPerOccurrence())
                .setScale(SCALE, RM);
        BigDecimal adjournmentContribution = rawAdjournmentContribution
                .min(weights.getMaxAdjournmentContribution())
                .setScale(SCALE, RM);
        boolean adjournmentCapped = rawAdjournmentContribution
                .compareTo(weights.getMaxAdjournmentContribution()) > 0;

        factors.add(ScoreFactorBreakdown.builder()
                .factorName("Prior Adjournments")
                .rawValue(BigDecimal.valueOf(adjournments).setScale(SCALE, RM))
                .weight(weights.getAdjournmentBoostPerOccurrence())
                .contribution(adjournmentContribution)
                .explanation(buildAdjournmentExplanation(adjournments,
                        weights.getAdjournmentBoostPerOccurrence(),
                        adjournmentContribution, weights.getMaxAdjournmentContribution(),
                        adjournmentCapped))
                .build());

        // -----------------------------------------------------------------------
        // Factor 4: Statutory deadline proximity
        // -----------------------------------------------------------------------
        LocalDate deadline = legalCase.getStatutoryDeadline();
        BigDecimal deadlineContribution;
        BigDecimal deadlineRawValue;
        String deadlineExplanation;

        if (deadline == null) {
            deadlineContribution = BigDecimal.ZERO.setScale(SCALE, RM);
            deadlineRawValue = BigDecimal.ZERO.setScale(SCALE, RM);
            deadlineExplanation = "No statutory deadline set; contributes 0.00 points.";
        } else {
            long daysUntil = ChronoUnit.DAYS.between(today, deadline);
            deadlineRawValue = BigDecimal.valueOf(daysUntil).setScale(SCALE, RM);
            if (daysUntil <= 0) {
                deadlineContribution = weights.getMaxDeadlineBonus().setScale(SCALE, RM);
                deadlineExplanation = daysUntil == 0
                        ? String.format("Statutory deadline is today; maximum urgency bonus applied: %.2f points.", deadlineContribution)
                        : String.format("Statutory deadline expired %d day(s) ago; maximum urgency bonus applied: %.2f points.",
                        Math.abs(daysUntil), deadlineContribution);
            } else {
                deadlineContribution = weights.getMaxDeadlineBonus()
                        .subtract(BigDecimal.valueOf(daysUntil * 2L))
                        .max(BigDecimal.ZERO)
                        .setScale(SCALE, RM);
                deadlineExplanation = String.format(
                        "Statutory deadline in %d day(s); escalating proximity bonus of %.2f points applied.",
                        daysUntil, deadlineContribution);
            }
        }

        factors.add(ScoreFactorBreakdown.builder()
                .factorName("Statutory Deadline Proximity")
                .rawValue(deadlineRawValue)
                .weight(weights.getMaxDeadlineBonus())
                .contribution(deadlineContribution)
                .explanation(deadlineExplanation)
                .build());

        // -----------------------------------------------------------------------
        // Factor 5: Linked-case status
        // -----------------------------------------------------------------------
        boolean hasLinkedCase = legalCase.getLinkedCase() != null;
        BigDecimal linkedContribution = hasLinkedCase
                ? weights.getLinkedCaseBonus().setScale(SCALE, RM)
                : BigDecimal.ZERO.setScale(SCALE, RM);

        factors.add(ScoreFactorBreakdown.builder()
                .factorName("Linked Case Status")
                .rawValue(hasLinkedCase ? BigDecimal.ONE.setScale(SCALE, RM) : BigDecimal.ZERO.setScale(SCALE, RM))
                .weight(weights.getLinkedCaseBonus())
                .contribution(linkedContribution)
                .explanation(buildLinkedCaseExplanation(hasLinkedCase, legalCase.getLinkedCase(),
                        weights.getLinkedCaseBonus()))
                .build());

        // -----------------------------------------------------------------------
        // Total
        // -----------------------------------------------------------------------
        BigDecimal total = typeUrgency
                .add(agingContribution)
                .add(adjournmentContribution)
                .add(deadlineContribution)
                .add(linkedContribution)
                .setScale(SCALE, RM);

        // Sort factors by contribution descending for dashboard readability
        factors.sort((a, b) -> b.getContribution().compareTo(a.getContribution()));

        String summary = buildSummary(legalCase, total, safeDays, adjournments);

        log.debug("Priority score for case {}: {} (type={}, days={}, adj={}, deadline={}, linked={})",
                legalCase.getCaseNumber(), total, legalCase.getCaseType(), safeDays,
                adjournments, deadlineContribution, hasLinkedCase);

        return PriorityScoreResult.builder()
                .caseId(legalCase.getId())
                .caseNumber(legalCase.getCaseNumber())
                .totalScore(total)
                .factors(factors)
                .summary(summary)
                .computedAt(LocalDateTime.now())
                .persistedScoreId(null)  // filled in by PriorityScoreService after save
                .build();
    }

    // =========================================================================
    // Private explanation builders
    // =========================================================================

    private String buildTypeExplanation(CaseType caseType, BigDecimal contribution) {
        return switch (caseType) {
            case BAIL -> String.format(
                    "BAIL case: highest statutory urgency due to personal liberty at stake "
                    + "(Art. 21, Constitution of India); contributes %.2f points.",
                    contribution);
            case POCSO -> String.format(
                    "POCSO case: highest statutory urgency under Section 35 POCSO Act 2012 "
                    + "(mandatory time-bound trial); contributes %.2f points.",
                    contribution);
            case MATRIMONIAL -> String.format(
                    "MATRIMONIAL case: moderate urgency; contributes %.2f points.",
                    contribution);
            case CRIMINAL_OTHER -> String.format(
                    "CRIMINAL (Other) case: general criminal urgency; contributes %.2f points.",
                    contribution);
            case CIVIL -> String.format(
                    "CIVIL case: standard urgency; contributes %.2f points.",
                    contribution);
        };
    }

    private String buildAgingExplanation(long days, BigDecimal ratePerDay,
                                         BigDecimal contribution, BigDecimal cap, boolean capped) {
        if (capped) {
            return String.format(
                    "Case has been pending for %d days (%.2f pts/day = %.2f raw), "
                    + "capped at %.2f; contributes %.2f points.",
                    days, ratePerDay, days * ratePerDay.doubleValue(), cap, contribution);
        }
        return String.format(
                "Case has been pending for %d days × %.2f pts/day; contributes %.2f points.",
                days, ratePerDay, contribution);
    }

    private String buildAdjournmentExplanation(int count, BigDecimal penaltyEach,
                                                BigDecimal contribution, BigDecimal cap, boolean capped) {
        if (count == 0) {
            return "No prior adjournments recorded; contributes 0.00 points.";
        }
        if (capped) {
            return String.format(
                    "%d prior adjournment(s) × %.2f pts each = %.2f raw, capped at %.2f; "
                    + "contributes %.2f points.",
                    count, penaltyEach, count * penaltyEach.doubleValue(), cap, contribution);
        }
        return String.format(
                "%d prior adjournment(s) × %.2f pts each; contributes %.2f points.",
                count, penaltyEach, contribution);
    }

    private String buildLinkedCaseExplanation(boolean hasLinkedCase, Case linkedCase,
                                               BigDecimal bonus) {
        if (!hasLinkedCase) {
            return "No linked case; contributes 0.00 points.";
        }
        String linkedNum = linkedCase != null ? linkedCase.getCaseNumber() : "unknown";
        return String.format(
                "Linked to case %s; resolving this case may unblock the linked proceeding; "
                + "contributes %.2f points.",
                linkedNum, bonus);
    }

    private String buildSummary(Case legalCase, BigDecimal total, long days, int adjournments) {
        String statusLabel = legalCase.getCurrentStatus() == CaseStatus.FILED ? "pending" : "active";
        return String.format(
                "Score %.2f: %s case %s %s for %d day(s) with %d prior adjournment(s)%s.",
                total,
                legalCase.getCaseType(),
                legalCase.getCaseNumber(),
                statusLabel,
                days,
                adjournments,
                legalCase.getLinkedCase() != null ? ", linked to another case" : "");
    }
}
