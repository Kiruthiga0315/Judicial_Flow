package com.judicialflow.simulation.validation;

import lombok.Builder;
import lombok.Data;

/**
 * Summary statistics across seeds for a specific scenario metric.
 */
@Data
@Builder
public class MetricSummary {
    private String metricName;
    private double fcfsMean;
    private double fcfsStd;
    private double engineMean;
    private double engineStd;
    private double pairedDiffMean; // engine - baseline
    private double pairedDiffStd;
    private double ci95Lower;
    private double ci95Upper;
    private double relativeChangePercent;
    private String outcome; // "Improved", "Worse", "No significant difference", or "PASS (tolerance ±...)"
    private boolean engineImproved;
    private boolean parityCheck;
    private Double parityTolerance;
    private Boolean parityPassed;
}
