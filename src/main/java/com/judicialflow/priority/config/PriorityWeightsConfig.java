package com.judicialflow.priority.config;

import com.judicialflow.common.enums.CaseType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Single source of truth for all priority scoring weights.
 *
 * <p>Design rationale:
 * <ul>
 *   <li><b>caseTypeUrgency</b> – statutory urgency level per case type.
 *       BAIL and POCSO carry the highest urgency because bail delay
 *       implicates liberty rights (Art. 21) and POCSO mandates
 *       time-bound trials under Section 35 POCSO Act 2012.</li>
 *   <li><b>agingWeightPerDay</b> – every calendar day the case sits
 *       unresolved adds this fraction to the score, capped at
 *       {@code maxAgingContribution} so very old cases don't dominate
 *       entirely.</li>
 *   <li><b>adjournmentBoostPerOccurrence</b> – each prior adjournment
 *       signals repeat delay and adds a fixed penalty.</li>
 *   <li><b>linkedCaseBonus</b> – a case that is linked to another
 *       open case gets a small urgency bump because resolving it may
 *       unblock the linked proceeding.</li>
 * </ul>
 *
 * <p>All weights are exposed as configurable properties under the prefix
 * {@code priority.weights} so they can be overridden in application.yml
 * without a recompile, and reviewed by non-engineers.
 */
@Component
@ConfigurationProperties(prefix = "priority.weights")
public class PriorityWeightsConfig {

    // -----------------------------------------------------------------------
    // Case-type urgency scores (raw additive contribution, 0–100 range)
    // -----------------------------------------------------------------------

    /**
     * Per-type statutory urgency scores.
     * Defaults are pre-populated in the constructor; application.yml entries
     * under {@code priority.weights.case-type-urgency.<TYPE>} override them.
     */
    private Map<CaseType, BigDecimal> caseTypeUrgency = new EnumMap<>(CaseType.class);

    // -----------------------------------------------------------------------
    // Aging factor
    // -----------------------------------------------------------------------

    /**
     * Score added per calendar day the case has been pending.
     * Default: 0.10 points/day → a 100-day-old case contributes 10 points
     * (before the cap).
     */
    private BigDecimal agingWeightPerDay = new BigDecimal("0.10");

    /**
     * Maximum score contribution from the aging factor alone.
     * Prevents ancient cases from overshadowing newer high-urgency ones.
     * Default: 40.
     */
    private BigDecimal maxAgingContribution = new BigDecimal("40.00");

    // -----------------------------------------------------------------------
    // Adjournment boost
    // -----------------------------------------------------------------------

    /**
     * Score added for each recorded prior adjournment.
     * Default: 5 points per adjournment.
     */
    private BigDecimal adjournmentBoostPerOccurrence = new BigDecimal("5.00");

    /**
     * Maximum score contribution from adjournments.
     * Default: 30 (caps at six adjournments at the default rate).
     */
    private BigDecimal maxAdjournmentContribution = new BigDecimal("30.00");

    // -----------------------------------------------------------------------
    // Linked-case bonus
    // -----------------------------------------------------------------------

    /**
     * Flat bonus added when the case has a non-null linked case.
     * Default: 10 points.
     */
    private BigDecimal linkedCaseBonus = new BigDecimal("10.00");

    // -----------------------------------------------------------------------
    // Statutory deadline proximity bonus
    // -----------------------------------------------------------------------

    /**
     * Maximum bonus for statutory deadline urgency (escalating as deadline approaches or passes).
     * Default: 30.00 points.
     */
    private BigDecimal maxDeadlineBonus = new BigDecimal("30.00");

    // -----------------------------------------------------------------------
    // Constructor – set default case-type urgency values
    // -----------------------------------------------------------------------

    public PriorityWeightsConfig() {
        // BAIL: personal liberty; constitutional urgency
        caseTypeUrgency.put(CaseType.BAIL, new BigDecimal("50.00"));
        // POCSO: statutory time-bound mandate (Section 35, POCSO Act 2012)
        caseTypeUrgency.put(CaseType.POCSO, new BigDecimal("50.00"));
        // MATRIMONIAL: moderate urgency
        caseTypeUrgency.put(CaseType.MATRIMONIAL, new BigDecimal("25.00"));
        // CRIMINAL_OTHER: general criminal urgency
        caseTypeUrgency.put(CaseType.CRIMINAL_OTHER, new BigDecimal("20.00"));
        // CIVIL: lowest statutory urgency
        caseTypeUrgency.put(CaseType.CIVIL, new BigDecimal("10.00"));
    }

    // -----------------------------------------------------------------------
    // Accessors (Spring needs setters for @ConfigurationProperties binding)
    // -----------------------------------------------------------------------

    public Map<CaseType, BigDecimal> getCaseTypeUrgency() {
        return caseTypeUrgency;
    }

    public void setCaseTypeUrgency(Map<CaseType, BigDecimal> caseTypeUrgency) {
        this.caseTypeUrgency = caseTypeUrgency;
    }

    public BigDecimal getAgingWeightPerDay() {
        return agingWeightPerDay;
    }

    public void setAgingWeightPerDay(BigDecimal agingWeightPerDay) {
        this.agingWeightPerDay = agingWeightPerDay;
    }

    public BigDecimal getMaxAgingContribution() {
        return maxAgingContribution;
    }

    public void setMaxAgingContribution(BigDecimal maxAgingContribution) {
        this.maxAgingContribution = maxAgingContribution;
    }

    public BigDecimal getAdjournmentBoostPerOccurrence() {
        return adjournmentBoostPerOccurrence;
    }

    public void setAdjournmentBoostPerOccurrence(BigDecimal adjournmentBoostPerOccurrence) {
        this.adjournmentBoostPerOccurrence = adjournmentBoostPerOccurrence;
    }

    public BigDecimal getMaxAdjournmentContribution() {
        return maxAdjournmentContribution;
    }

    public void setMaxAdjournmentContribution(BigDecimal maxAdjournmentContribution) {
        this.maxAdjournmentContribution = maxAdjournmentContribution;
    }

    public BigDecimal getLinkedCaseBonus() {
        return linkedCaseBonus;
    }

    public void setLinkedCaseBonus(BigDecimal linkedCaseBonus) {
        this.linkedCaseBonus = linkedCaseBonus;
    }

    public BigDecimal getMaxDeadlineBonus() {
        return maxDeadlineBonus;
    }

    public void setMaxDeadlineBonus(BigDecimal maxDeadlineBonus) {
        this.maxDeadlineBonus = maxDeadlineBonus;
    }
}
