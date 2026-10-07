package com.judicialflow.simulation;

import com.judicialflow.common.enums.CaseType;
import java.util.Map;

/**
 * Synthetic Caseload Calibration Constants & Source Attribution.
 *
 * NOTE ON NJDG DATA:
 * The National Judicial Data Grid (NJDG) public portal reports cumulative pending stock,
 * NOT incoming filing flow/arrival ratios. A pending-stock ratio is not an arrival ratio.
 * Consequently, in the absence of verified empirical microdata with explicit URL, table name,
 * and access date, ALL caseload calibration targets below are strictly designated as ASSUMPTIONS.
 *
 * A dedicated placeholder is provided in docs/simulation-methodology.md for users to record
 * verified empirical sources and calibration dates.
 */
public final class NjdgCalibrationTargets {

    private NjdgCalibrationTargets() {}

    /**
     * ASSUMPTION: Case type distribution targets for synthetic arrivals.
     * Derived from illustrative approximations of broad pending stock, not verified filing flow.
     */
    public static final Map<CaseType, Double> CASE_TYPE_PERCENTAGES = Map.of(
            CaseType.BAIL, 10.0,            // ASSUMPTION
            CaseType.POCSO, 5.0,            // ASSUMPTION
            CaseType.CRIMINAL_OTHER, 60.0,  // ASSUMPTION
            CaseType.CIVIL, 20.0,           // ASSUMPTION
            CaseType.MATRIMONIAL, 5.0       // ASSUMPTION
    );

    /**
     * ASSUMPTION: Illustrative 20% disposal rate for synthetic caseload seeding.
     */
    public static final double DISPOSAL_RATE_PERCENTAGE = 20.0;
}
