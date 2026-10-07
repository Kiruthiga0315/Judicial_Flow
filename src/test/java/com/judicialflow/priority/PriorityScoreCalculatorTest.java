package com.judicialflow.priority;

import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.models.Case;
import com.judicialflow.priority.config.PriorityWeightsConfig;
import com.judicialflow.priority.dto.PriorityScoreResult;
import com.judicialflow.priority.dto.ScoreFactorBreakdown;
import com.judicialflow.priority.service.PriorityScoreCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PriorityScoreCalculator}.
 *
 * <h2>Test philosophy</h2>
 * Every test asserts:
 * <ol>
 *   <li>The <strong>exact total score</strong> (not just "a number comes out").</li>
 *   <li>The <strong>exact contribution</strong> of each factor in the breakdown.</li>
 *   <li>The <strong>breakdown list size</strong> (four factors always present).</li>
 *   <li>The <strong>explanation text</strong> contains key expected phrases.</li>
 * </ol>
 *
 * <p>The {@link PriorityWeightsConfig} is constructed directly (no Spring context
 * needed) so the tests are fast, deterministic, and independent of application.yml.
 */
@DisplayName("PriorityScoreCalculator unit tests")
class PriorityScoreCalculatorTest {

    /** Default weights used in most tests (matches production defaults). */
    private PriorityWeightsConfig defaultWeights;
    private PriorityScoreCalculator calculator;

    // =========================================================================
    // Test fixtures
    // =========================================================================

    /** Convenience: build a minimal Case with a given type, filing date, adjournments, linked. */
    private Case buildCase(CaseType type, LocalDate filingDate, int adjournments, Case linkedCase) {
        return buildCase(type, filingDate, adjournments, linkedCase, null);
    }

    private Case buildCase(CaseType type, LocalDate filingDate, int adjournments, Case linkedCase, LocalDate statutoryDeadline) {
        return Case.builder()
                .id(UUID.randomUUID())
                .caseNumber("TEST-2024-" + type.name())
                .caseType(type)
                .filingDate(filingDate)
                .currentStatus(CaseStatus.FILED)
                .priorAdjournments(adjournments)
                .linkedCase(linkedCase)
                .statutoryDeadline(statutoryDeadline)
                .deleted(false)
                .build();
    }

    @BeforeEach
    void setUp() {
        defaultWeights = new PriorityWeightsConfig();
        calculator = new PriorityScoreCalculator(defaultWeights);
    }

    // =========================================================================
    // Helper: look up a factor breakdown by name
    // =========================================================================

    private ScoreFactorBreakdown factor(PriorityScoreResult result, String name) {
        return result.getFactors().stream()
                .filter(f -> name.equals(f.getFactorName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Factor '" + name + "' not found in breakdown"));
    }

    // =========================================================================
    // Test: BAIL case — highest urgency
    // =========================================================================

    @Nested
    @DisplayName("BAIL case scoring")
    class BailCaseTests {

        @Test
        @DisplayName("BAIL: type urgency contributes 50, aging and adjournments are additive")
        void bailCaseFullScore() {
            // Given
            // Filing date 100 days ago → aging = 100 × 0.10 = 10.00 (not capped)
            LocalDate filingDate = LocalDate.now().minusDays(100);
            Case legalCase = buildCase(CaseType.BAIL, filingDate, 3, null);
            // adjournments: 3 × 5.00 = 15.00

            // When
            PriorityScoreResult result = calculator.calculate(legalCase);

            // Then – exact score assertion
            // 50 (BAIL) + 10 (aging) + 15 (adj) + 0 (no linked) = 75.0000
            assertThat(result.getTotalScore()).isEqualByComparingTo("75.0000");

            // And – factor breakdown assertions
            assertThat(result.getFactors()).hasSize(5);

            ScoreFactorBreakdown typeF = factor(result, "Case Type Urgency");
            assertThat(typeF.getContribution()).isEqualByComparingTo("50.0000");
            assertThat(typeF.getExplanation()).contains("BAIL");

            ScoreFactorBreakdown agingF = factor(result, "Time Pending (Aging)");
            assertThat(agingF.getRawValue()).isEqualByComparingTo("100.0000");
            assertThat(agingF.getWeight()).isEqualByComparingTo("0.1000");
            assertThat(agingF.getContribution()).isEqualByComparingTo("10.0000");

            ScoreFactorBreakdown adjF = factor(result, "Prior Adjournments");
            assertThat(adjF.getRawValue()).isEqualByComparingTo("3.0000");
            assertThat(adjF.getContribution()).isEqualByComparingTo("15.0000");

            ScoreFactorBreakdown linkedF = factor(result, "Linked Case Status");
            assertThat(linkedF.getContribution()).isEqualByComparingTo("0.0000");
            assertThat(linkedF.getExplanation()).contains("No linked case");
        }
    }

    // =========================================================================
    // Test: POCSO case — highest urgency (equal to BAIL)
    // =========================================================================

    @Nested
    @DisplayName("POCSO case scoring")
    class PocsoCaseTests {

        @Test
        @DisplayName("POCSO: type urgency equals BAIL (50 points)")
        void pocsoUrgencyEqualsBail() {
            Case bail  = buildCase(CaseType.BAIL,  LocalDate.now().minusDays(50), 0, null);
            Case pocso = buildCase(CaseType.POCSO, LocalDate.now().minusDays(50), 0, null);

            PriorityScoreResult bailResult  = calculator.calculate(bail);
            PriorityScoreResult pocsoResult = calculator.calculate(pocso);

            // Type urgency must be identical
            assertThat(factor(bailResult, "Case Type Urgency").getContribution())
                    .isEqualByComparingTo(factor(pocsoResult, "Case Type Urgency").getContribution());
            // Total scores must also be identical
            assertThat(bailResult.getTotalScore()).isEqualByComparingTo(pocsoResult.getTotalScore());
        }

        @Test
        @DisplayName("POCSO: explanation mentions POCSO Act 2012")
        void pocsoExplanationMentionsPocsoAct() {
            Case legalCase = buildCase(CaseType.POCSO, LocalDate.now().minusDays(10), 0, null);
            PriorityScoreResult result = calculator.calculate(legalCase);

            assertThat(factor(result, "Case Type Urgency").getExplanation())
                    .contains("POCSO Act 2012");
        }
    }

    // =========================================================================
    // Test: Aging cap
    // =========================================================================

    @Nested
    @DisplayName("Aging contribution cap")
    class AgingCapTests {

        @Test
        @DisplayName("Aging is capped at maxAgingContribution (40) even for very old cases")
        void agingCappedAt40() {
            // 600 days × 0.10 = 60 raw → capped at 40
            LocalDate filingDate = LocalDate.now().minusDays(600);
            Case legalCase = buildCase(CaseType.CIVIL, filingDate, 0, null);

            PriorityScoreResult result = calculator.calculate(legalCase);

            ScoreFactorBreakdown agingF = factor(result, "Time Pending (Aging)");
            assertThat(agingF.getContribution()).isEqualByComparingTo("40.0000");
            assertThat(agingF.getExplanation()).contains("capped");
        }

        @Test
        @DisplayName("Aging is not capped when below the threshold")
        void agingNotCappedWhenBelow() {
            // 200 days × 0.10 = 20 < 40
            LocalDate filingDate = LocalDate.now().minusDays(200);
            Case legalCase = buildCase(CaseType.CIVIL, filingDate, 0, null);

            PriorityScoreResult result = calculator.calculate(legalCase);

            ScoreFactorBreakdown agingF = factor(result, "Time Pending (Aging)");
            assertThat(agingF.getContribution()).isEqualByComparingTo("20.0000");
            assertThat(agingF.getExplanation()).doesNotContain("capped");
        }
    }

    // =========================================================================
    // Test: Adjournment cap
    // =========================================================================

    @Nested
    @DisplayName("Adjournment boost cap")
    class AdjournmentCapTests {

        @Test
        @DisplayName("Adjournment boost is capped at maxAdjournmentContribution (30)")
        void adjournmentCappedAt30() {
            // 10 adjournments × 5.00 = 50 raw → capped at 30
            Case legalCase = buildCase(CaseType.CIVIL, LocalDate.now().minusDays(10), 10, null);

            PriorityScoreResult result = calculator.calculate(legalCase);

            ScoreFactorBreakdown adjF = factor(result, "Prior Adjournments");
            assertThat(adjF.getContribution()).isEqualByComparingTo("30.0000");
            assertThat(adjF.getExplanation()).contains("capped");
        }

        @Test
        @DisplayName("Zero adjournments contribute 0 and explanation says 'No prior adjournments'")
        void zeroAdjournments() {
            Case legalCase = buildCase(CaseType.BAIL, LocalDate.now().minusDays(30), 0, null);

            PriorityScoreResult result = calculator.calculate(legalCase);

            ScoreFactorBreakdown adjF = factor(result, "Prior Adjournments");
            assertThat(adjF.getContribution()).isEqualByComparingTo("0.0000");
            assertThat(adjF.getExplanation()).contains("No prior adjournments");
        }
    }

    // =========================================================================
    // Test: Linked-case bonus
    // =========================================================================

    @Nested
    @DisplayName("Linked case bonus")
    class LinkedCaseTests {

        @Test
        @DisplayName("Linked case adds the configured bonus (10.00)")
        void linkedCaseAddsBonus() {
            // Use the same case type and filing date so the ONLY difference is the linked-case bonus
            LocalDate filingDate = LocalDate.now().minusDays(50);
            Case parentA = buildCase(CaseType.CIVIL, filingDate, 0, null);
            Case withLinked    = buildCase(CaseType.CIVIL, filingDate, 0, parentA);
            Case withoutLinked = buildCase(CaseType.CIVIL, filingDate, 0, null);

            PriorityScoreResult linkedResult    = calculator.calculate(withLinked);
            PriorityScoreResult noLinkedResult  = calculator.calculate(withoutLinked);

            ScoreFactorBreakdown linkedFactor    = factor(linkedResult, "Linked Case Status");
            ScoreFactorBreakdown noLinkedFactor  = factor(noLinkedResult, "Linked Case Status");

            assertThat(linkedFactor.getContribution()).isEqualByComparingTo("10.0000");
            assertThat(noLinkedFactor.getContribution()).isEqualByComparingTo("0.0000");

            // Difference in total scores must equal the bonus
            BigDecimal scoreDiff = linkedResult.getTotalScore().subtract(noLinkedResult.getTotalScore());
            assertThat(scoreDiff).isEqualByComparingTo("10.0000");
        }

        @Test
        @DisplayName("Linked case explanation mentions the linked case number")
        void linkedExplanationMentionsCaseNumber() {
            Case parent = buildCase(CaseType.CIVIL, LocalDate.now().minusDays(1), 0, null);
            Case child  = buildCase(CaseType.BAIL,  LocalDate.now().minusDays(1), 0, parent);

            PriorityScoreResult result = calculator.calculate(child);

            assertThat(factor(result, "Linked Case Status").getExplanation())
                    .contains(parent.getCaseNumber());
        }
    }

    // =========================================================================
    // Test: Exact score with all factors combined
    // =========================================================================

    @Nested
    @DisplayName("Full combined score precision test")
    class FullScoreTests {

        @Test
        @DisplayName("Known inputs produce exact total score and exact per-factor contributions")
        void knownInputsExactScore() {
            /*
             * Inputs (deterministic):
             *   type        = POCSO         → urgency = 50.0000
             *   days        = 180           → aging   = min(180×0.10, 40) = 18.0000 (not capped)
             *   adjournments= 2             → adj     = 2×5.00 = 10.0000
             *   linkedCase  = present       → bonus   = 10.0000
             *
             *   total = 50 + 18 + 10 + 10 = 88.0000
             */
            Case parent = buildCase(CaseType.CIVIL, LocalDate.now().minusDays(1), 0, null);
            Case legalCase  = buildCase(CaseType.POCSO, LocalDate.now().minusDays(180), 2, parent);

            PriorityScoreResult result = calculator.calculate(legalCase);

            // Total
            assertThat(result.getTotalScore()).isEqualByComparingTo("88.0000");

            // Each factor
            assertThat(factor(result, "Case Type Urgency").getContribution())
                    .isEqualByComparingTo("50.0000");
            assertThat(factor(result, "Time Pending (Aging)").getContribution())
                    .isEqualByComparingTo("18.0000");
            assertThat(factor(result, "Prior Adjournments").getContribution())
                    .isEqualByComparingTo("10.0000");
            assertThat(factor(result, "Linked Case Status").getContribution())
                    .isEqualByComparingTo("10.0000");

            // Summary present and non-blank
            assertThat(result.getSummary()).isNotBlank();
            assertThat(result.getSummary()).contains("POCSO");
        }

        @Test
        @DisplayName("Maximum score scenario: BAIL + 600 days + 10 adj + linked = 10+40+30+50 = 130")
        void maximumScoreScenario() {
            // BAIL urgency = 50, aging capped at 40, adjournment capped at 30, linked bonus 10
            Case parent = buildCase(CaseType.CIVIL, LocalDate.now().minusDays(1), 0, null);
            Case legalCase  = buildCase(CaseType.BAIL,  LocalDate.now().minusDays(600), 10, parent);

            PriorityScoreResult result = calculator.calculate(legalCase);

            assertThat(result.getTotalScore()).isEqualByComparingTo("130.0000");
        }
    }

    // =========================================================================
    // Test: Weights are read from config
    // =========================================================================

    @Nested
    @DisplayName("Custom weight configuration")
    class CustomWeightTests {

        @Test
        @DisplayName("Calculator respects custom adjournment penalty from config")
        void customAdjournmentWeight() {
            PriorityWeightsConfig custom = new PriorityWeightsConfig();
            custom.setAdjournmentBoostPerOccurrence(new BigDecimal("8.00"));
            custom.setMaxAdjournmentContribution(new BigDecimal("100.00")); // high cap

            PriorityScoreCalculator customCalc = new PriorityScoreCalculator(custom);

            Case legalCase = buildCase(CaseType.CIVIL, LocalDate.now().minusDays(1), 3, null);
            PriorityScoreResult result = customCalc.calculate(legalCase);

            // 3 × 8.00 = 24.00
            assertThat(factor(result, "Prior Adjournments").getContribution())
                    .isEqualByComparingTo("24.0000");
        }

        @Test
        @DisplayName("Calculator respects custom case-type urgency from config")
        void customCaseTypeUrgency() {
            PriorityWeightsConfig custom = new PriorityWeightsConfig();
            custom.getCaseTypeUrgency().put(CaseType.CIVIL, new BigDecimal("99.00"));

            PriorityScoreCalculator customCalc = new PriorityScoreCalculator(custom);

            Case legalCase = buildCase(CaseType.CIVIL, LocalDate.now().minusDays(1), 0, null);
            PriorityScoreResult result = customCalc.calculate(legalCase);

            assertThat(factor(result, "Case Type Urgency").getContribution())
                    .isEqualByComparingTo("99.0000");
        }
    }

    // =========================================================================
    // Test: Result structure guarantees
    // =========================================================================

    @Nested
    @DisplayName("Result structure guarantees")
    class ResultStructureTests {

        @Test
        @DisplayName("Result always contains exactly 5 factors")
        void alwaysFiveFactors() {
            Case legalCase = buildCase(CaseType.CRIMINAL_OTHER, LocalDate.now().minusDays(5), 1, null);
            PriorityScoreResult result = calculator.calculate(legalCase);
            assertThat(result.getFactors()).hasSize(5);
        }

        @Test
        @DisplayName("Sum of factor contributions equals the total score")
        void factorsSumToTotal() {
            Case legalCase = buildCase(CaseType.MATRIMONIAL, LocalDate.now().minusDays(90), 2, null);
            PriorityScoreResult result = calculator.calculate(legalCase);

            BigDecimal factorSum = result.getFactors().stream()
                    .map(ScoreFactorBreakdown::getContribution)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(factorSum).isEqualByComparingTo(result.getTotalScore());
        }

        @Test
        @DisplayName("Factors are sorted by contribution descending")
        void factorsSortedDescending() {
            // BAIL (50) > aging for 300 days capped at 40 > adjournments 2×5=10 > linked 0
            Case legalCase = buildCase(CaseType.BAIL, LocalDate.now().minusDays(300), 2, null);
            PriorityScoreResult result = calculator.calculate(legalCase);

            BigDecimal prev = BigDecimal.valueOf(Long.MAX_VALUE);
            for (ScoreFactorBreakdown f : result.getFactors()) {
                assertThat(f.getContribution())
                        .as("Factor '%s' out of order", f.getFactorName())
                        .isLessThanOrEqualTo(prev);
                prev = f.getContribution();
            }
        }

        @Test
        @DisplayName("caseId and caseNumber are populated correctly")
        void resultMetadata() {
            Case legalCase = buildCase(CaseType.CIVIL, LocalDate.now().minusDays(10), 0, null);
            PriorityScoreResult result = calculator.calculate(legalCase);

            assertThat(result.getCaseId()).isEqualTo(legalCase.getId());
            assertThat(result.getCaseNumber()).isEqualTo(legalCase.getCaseNumber());
            assertThat(result.getComputedAt()).isNotNull();
        }

        @Test
        @DisplayName("persistedScoreId is null from the calculator (set by service after save)")
        void persistedIdIsNull() {
            Case legalCase = buildCase(CaseType.CIVIL, LocalDate.now().minusDays(10), 0, null);
            PriorityScoreResult result = calculator.calculate(legalCase);
            assertThat(result.getPersistedScoreId()).isNull();
        }
    }

    @Nested
    @DisplayName("Fix 3: Statutory deadline & edge-case unit tests")
    class StatutoryDeadlineEdgeCaseTests {

        @Test
        @DisplayName("no deadline: contributes 0.00 points")
        void testNoDeadline() {
            Case legalCase = buildCase(CaseType.CIVIL, LocalDate.now().minusDays(10), 1, null, null);
            PriorityScoreResult result = calculator.calculate(legalCase);
            ScoreFactorBreakdown f = factor(result, "Statutory Deadline Proximity");
            assertThat(f.getContribution()).isEqualByComparingTo("0.0000");
            assertThat(f.getExplanation()).contains("No statutory deadline set");
        }

        @Test
        @DisplayName("deadline in 3 days: escalating proximity bonus applied")
        void testDeadlineInThreeDays() {
            Case legalCase = buildCase(CaseType.CIVIL, LocalDate.now().minusDays(10), 0, null, LocalDate.now().plusDays(3));
            PriorityScoreResult result = calculator.calculate(legalCase);
            ScoreFactorBreakdown f = factor(result, "Statutory Deadline Proximity");
            // 30 - (3 * 2) = 24.0000
            assertThat(f.getContribution()).isEqualByComparingTo("24.0000");
            assertThat(f.getExplanation()).contains("in 3 day(s)");
        }

        @Test
        @DisplayName("deadline already passed: max urgency bonus applied")
        void testDeadlineAlreadyPassed() {
            Case legalCase = buildCase(CaseType.CIVIL, LocalDate.now().minusDays(10), 0, null, LocalDate.now().minusDays(4));
            PriorityScoreResult result = calculator.calculate(legalCase);
            ScoreFactorBreakdown f = factor(result, "Statutory Deadline Proximity");
            assertThat(f.getContribution()).isEqualByComparingTo("30.0000");
            assertThat(f.getExplanation()).contains("expired 4 day(s) ago");
        }

        @Test
        @DisplayName("zero adjournments: contributes 0.00 points")
        void testZeroAdjournments() {
            Case legalCase = buildCase(CaseType.CIVIL, LocalDate.now().minusDays(10), 0, null, null);
            PriorityScoreResult result = calculator.calculate(legalCase);
            ScoreFactorBreakdown f = factor(result, "Prior Adjournments");
            assertThat(f.getContribution()).isEqualByComparingTo("0.0000");
            assertThat(f.getExplanation()).contains("No prior adjournments recorded");
        }

        @Test
        @DisplayName("brand-new case: 0 days pending, 0 adjournments, no deadline")
        void testBrandNewCase() {
            Case legalCase = buildCase(CaseType.CIVIL, LocalDate.now(), 0, null, null);
            PriorityScoreResult result = calculator.calculate(legalCase);
            // Civil: 10 base, 0 aging, 0 adj, 0 deadline, 0 linked = 10.0000
            assertThat(result.getTotalScore()).isEqualByComparingTo("10.0000");
            ScoreFactorBreakdown aging = factor(result, "Time Pending (Aging)");
            assertThat(aging.getContribution()).isEqualByComparingTo("0.0000");
        }

        @Test
        @DisplayName("very old case: aging is capped at maxAgingContribution")
        void testVeryOldCase() {
            Case legalCase = buildCase(CaseType.CIVIL, LocalDate.now().minusDays(2000), 0, null, null);
            PriorityScoreResult result = calculator.calculate(legalCase);
            ScoreFactorBreakdown aging = factor(result, "Time Pending (Aging)");
            assertThat(aging.getContribution()).isEqualByComparingTo(defaultWeights.getMaxAgingContribution());
            assertThat(aging.getExplanation()).contains("capped at");
        }
    }
}
