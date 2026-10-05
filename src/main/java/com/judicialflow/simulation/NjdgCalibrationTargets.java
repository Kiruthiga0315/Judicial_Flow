package com.judicialflow.simulation;

import com.judicialflow.common.enums.CaseType;
import java.util.Map;

/**
 * Approximate NJDG-calibrated targets.
 * Source: National Judicial Data Grid (NJDG) published aggregate statistics.
 * As of: January 2024 (Approximate)
 * These are approximate targets for synthetic data generation and do not claim to be exact.
 */
public final class NjdgCalibrationTargets {
    
    private NjdgCalibrationTargets() {}

    public static final Map<CaseType, Double> CASE_TYPE_PERCENTAGES = Map.of(
            CaseType.BAIL, 10.0,            // Source: approximate estimate
            CaseType.POCSO, 5.0,            // Source: approximate estimate
            CaseType.CRIMINAL_OTHER, 60.0,  // Source: approximate estimate
            CaseType.CIVIL, 20.0,           // Source: approximate estimate
            CaseType.MATRIMONIAL, 5.0       // Source: approximate estimate
    );

    /**
     * Approximate/illustrative disposal rate for synthetic case generation.
     * Represents the historical backlog of closed cases.
     */
    public static final double DISPOSAL_RATE_PERCENTAGE = 20.0;
}
