package com.judicialflow.simulation;

import com.judicialflow.common.enums.CaseType;
import java.util.Map;

/**
 * Approximate NJDG-calibrated targets.
 * Source: National Judicial Data Grid (NJDG) published aggregate statistics.
 * These are approximate targets for synthetic data generation and do not claim to be exact.
 */
public final class NjdgCalibrationTargets {
    
    private NjdgCalibrationTargets() {}

    public static final Map<CaseType, Double> CASE_TYPE_PERCENTAGES = Map.of(
            CaseType.BAIL, 10.0,
            CaseType.POCSO, 5.0,
            CaseType.CRIMINAL_OTHER, 60.0,
            CaseType.CIVIL, 20.0,
            CaseType.MATRIMONIAL, 5.0
    );
}
