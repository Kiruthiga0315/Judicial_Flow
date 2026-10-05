package com.judicialflow.priority.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.models.Case;
import com.judicialflow.common.models.PriorityScore;
import com.judicialflow.ingestion.exceptions.ResourceNotFoundException;
import com.judicialflow.priority.dto.PriorityScoreResult;
import com.judicialflow.priority.dto.ScoreFactorBreakdown;
import com.judicialflow.priority.repository.PriorityScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Orchestration service for priority scoring.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Load the {@link Case} entity (and verify it exists / is not deleted).</li>
 *   <li>Delegate pure computation to {@link PriorityScoreCalculator}.</li>
 *   <li>Persist the result to the {@code priority_scores} table (score history).</li>
 *   <li>Return the enriched {@link PriorityScoreResult} (with the persisted record's ID).</li>
 *   <li>Support bulk operations: compute+persist all open cases, and retrieve the top-N.</li>
 * </ol>
 *
 * <h2>Persistence strategy</h2>
 * Each call appends a <em>new</em> {@link PriorityScore} row.  Score history is
 * therefore queryable; the latest row for any case is what the REST endpoints surface.
 * The {@code explanation} column stores the JSON-serialised factor breakdown so that
 * historical scores are fully self-contained.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriorityScoreService {

    private final CaseRepository caseRepository;
    private final PriorityScoreRepository priorityScoreRepository;
    private final PriorityScoreCalculator calculator;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Compute (and persist) the priority score for a single case.
     *
     * @param caseId UUID of the case
     * @return fully populated {@link PriorityScoreResult} including the DB record ID
     * @throws ResourceNotFoundException if the case does not exist or is soft-deleted
     */
    @Transactional
    public PriorityScoreResult computeAndPersist(UUID caseId, String triggeredBy) {
        Case legalCase = caseRepository.findByIdAndDeletedFalse(caseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Case not found with id: " + caseId));

        PriorityScoreResult result = calculator.calculate(legalCase);
        PriorityScore persisted = persist(legalCase, result, triggeredBy);

        return withPersistedId(result, persisted.getId());
    }

    /**
     * Retrieve the latest persisted score for a case (re-computes and persists
     * if no previous score exists).
     *
     * @param caseId UUID of the case
     * @return the latest {@link PriorityScoreResult}
     */
    @Transactional
    public PriorityScoreResult getOrComputeLatest(UUID caseId) {
        Case legalCase = caseRepository.findByIdAndDeletedFalse(caseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Case not found with id: " + caseId));

        return priorityScoreRepository.findLatestByCaseId(caseId)
                .map(ps -> mapToResult(legalCase, ps))
                .orElseGet(() -> {
                    PriorityScoreResult fresh = calculator.calculate(legalCase);
                    PriorityScore persisted = persist(legalCase, fresh, "MANUAL");
                    return withPersistedId(fresh, persisted.getId());
                });
    }

    /**
     * Compute fresh scores for ALL non-deleted, non-disposed open cases and
     * return the top {@code limit} by score descending.
     *
     * <p>Use this to drive the registrar's "urgent cases" dashboard view.
     *
     * @param limit maximum number of results (must be ≥ 1)
     * @return ordered list of scored results, highest score first
     */
    @Transactional
    public List<PriorityScoreResult> computeTopN(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }

        List<Case> openCases = caseRepository.findAll().stream()
                .filter(c -> !c.isDeleted())
                .filter(c -> c.getCurrentStatus() != CaseStatus.DISPOSED)
                .toList();

        log.info("Computing scores for {} open non-disposed cases", openCases.size());

        List<PriorityScore> scored = openCases.stream()
                .map(c -> {
                    PriorityScoreResult r = calculator.calculate(c);
                    return persist(c, r, "BATCH");
                })
                .sorted((a, b) -> b.getTotalScore().compareTo(a.getTotalScore()))
                .limit(limit)
                .toList();

        // Re-load full Case entities for the top-N to build results
        return scored.stream()
                .map(ps -> {
                    Case c = ps.getLegalCase();
                    return mapToResult(c, ps);
                })
                .toList();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Persist a computed result as a new {@link PriorityScore} row.
     */
    private PriorityScore persist(Case legalCase, PriorityScoreResult result, String triggeredBy) {
        // Extract named factor contributions for the individual columns
        BigDecimal typeUrgency = factorContribution(result, "Case Type Urgency");
        BigDecimal aging = factorContribution(result, "Time Pending (Aging)");
        BigDecimal adjournment = factorContribution(result, "Prior Adjournments");

        // Serialize the full breakdown to JSON for the explanation column
        String explanationJson = serializeBreakdown(result);

        BigDecimal linkedBonus = factorContribution(result, "Linked Case Status");

        PriorityScore entity = PriorityScore.builder()
                .legalCase(legalCase)
                .totalScore(result.getTotalScore())
                .baseWeight(typeUrgency)          // mapped: case-type urgency → base weight
                .ageMultiplier(aging)             // mapped: aging contribution → age multiplier
                .adjournmentBoost(adjournment)  // mapped: adjournment contribution
                .linkedCaseBonus(linkedBonus)     // mapped: linked-case bonus
                .explanation(explanationJson)
                .triggeredBy(triggeredBy)
                .build();

        PriorityScore saved = priorityScoreRepository.save(entity);
        log.debug("Persisted PriorityScore id={} for case {}", saved.getId(), legalCase.getCaseNumber());
        return saved;
    }

    /**
     * Extract the contribution of a named factor from the result's factor list.
     */
    private BigDecimal factorContribution(PriorityScoreResult result, String factorName) {
        return result.getFactors().stream()
                .filter(f -> factorName.equals(f.getFactorName()))
                .map(ScoreFactorBreakdown::getContribution)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Serialise the factor breakdown list to JSON.  Stored in the {@code explanation}
     * column so historical records are self-documenting.
     */
    private String serializeBreakdown(PriorityScoreResult result) {
        try {
            return objectMapper.writeValueAsString(result.getFactors());
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize breakdown to JSON for case {}; falling back to summary",
                    result.getCaseNumber(), e);
            return result.getSummary();
        }
    }

    /**
     * Reconstruct a {@link PriorityScoreResult} from a persisted
     * {@link PriorityScore} entity (re-uses the stored JSON explanation).
     */
    private PriorityScoreResult mapToResult(Case legalCase, PriorityScore ps) {
        List<ScoreFactorBreakdown> factors = deserializeBreakdown(ps.getExplanation());

        String summary = String.format("Score %.2f: %s case %s (from stored record computed at %s).",
                ps.getTotalScore(), legalCase.getCaseType(), legalCase.getCaseNumber(), ps.getComputedAt());

        return PriorityScoreResult.builder()
                .caseId(legalCase.getId())
                .caseNumber(legalCase.getCaseNumber())
                .totalScore(ps.getTotalScore())
                .factors(factors)
                .summary(summary)
                .computedAt(ps.getComputedAt())
                .persistedScoreId(ps.getId())
                .build();
    }

    /**
     * Deserialise the JSON breakdown from the {@code explanation} column.
     * Falls back to an empty list if the column holds legacy plain-text.
     */
    private List<ScoreFactorBreakdown> deserializeBreakdown(String json) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, ScoreFactorBreakdown.class));
        } catch (Exception e) {
            log.warn("Could not deserialize breakdown JSON; returning empty list. Content: {}",
                    json, e);
            return List.of();
        }
    }

    /**
     * Return a new result record with the persisted score's UUID filled in.
     */
    private PriorityScoreResult withPersistedId(PriorityScoreResult result, UUID persistedId) {
        return PriorityScoreResult.builder()
                .caseId(result.getCaseId())
                .caseNumber(result.getCaseNumber())
                .totalScore(result.getTotalScore())
                .factors(result.getFactors())
                .summary(result.getSummary())
                .computedAt(result.getComputedAt())
                .persistedScoreId(persistedId)
                .build();
    }
}
