package com.judicialflow.simulation.validation;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Calculates mean, std, paired difference, 95% Student's t confidence interval,
 * outcome labels by 95% CI only ("Improved", "Worse", "No significant difference"),
 * relative change in %, and explicit tolerance-based parity checks across seeds.
 */
@Component
public class SimulationStatsCalculator {

    // Student's t critical values for two-tailed 95% confidence interval
    // Index: degrees of freedom (df = n - 1)
    private static final double[] T_CRITICAL_95 = {
            0.0,    // df=0 (dummy)
            12.706, // df=1
            4.303,  // df=2
            3.182,  // df=3
            2.776,  // df=4
            2.571,  // df=5
            2.447,  // df=6
            2.365,  // df=7
            2.306,  // df=8
            2.262,  // df=9
            2.228,  // df=10
            2.201,  // df=11
            2.179,  // df=12
            2.160,  // df=13
            2.145,  // df=14
            2.131,  // df=15
            2.120,  // df=16
            2.110,  // df=17
            2.101,  // df=18
            2.093,  // df=19 (20 seeds)
            2.086   // df=20
    };

    public MetricSummary computeSummary(
            String metricName,
            List<ArmMetrics> fcfsList,
            List<ArmMetrics> engineList,
            ToDoubleFunction<ArmMetrics> extractor,
            boolean lowerIsBetter
    ) {
        return computeSummary(metricName, fcfsList, engineList, extractor, lowerIsBetter, false, null);
    }

    public MetricSummary computeSummary(
            String metricName,
            List<ArmMetrics> fcfsList,
            List<ArmMetrics> engineList,
            ToDoubleFunction<ArmMetrics> extractor,
            boolean lowerIsBetter,
            boolean isParityCheck,
            Double parityTolerance
    ) {
        int n = fcfsList.size();
        if (n == 0) {
            return MetricSummary.builder().metricName(metricName).build();
        }

        double[] fcfsVals = new double[n];
        double[] engineVals = new double[n];
        double[] diffs = new double[n];

        for (int i = 0; i < n; i++) {
            fcfsVals[i] = extractor.applyAsDouble(fcfsList.get(i));
            engineVals[i] = extractor.applyAsDouble(engineList.get(i));
            diffs[i] = engineVals[i] - fcfsVals[i]; // paired difference (Engine - Baseline)
        }

        double fcfsMean = mean(fcfsVals);
        double fcfsStd = sampleStdDev(fcfsVals, fcfsMean);

        double engineMean = mean(engineVals);
        double engineStd = sampleStdDev(engineVals, engineMean);

        double diffMean = mean(diffs);
        double diffStd = sampleStdDev(diffs, diffMean);

        // 95% Confidence Interval for diffMean: diffMean ± t * (diffStd / sqrt(n))
        double tCrit = getTCritical(n - 1);
        double margin = n > 1 ? tCrit * (diffStd / Math.sqrt(n)) : 0.0;
        double ciLower = diffMean - margin;
        double ciUpper = diffMean + margin;

        // Relative change in %: (diffMean / fcfsMean) * 100
        double relChange = Math.abs(fcfsMean) > 1e-6 ? (diffMean / fcfsMean) * 100.0 : 0.0;

        String outcome;
        boolean engineImproved = false;
        Boolean parityPassed = null;

        if (isParityCheck) {
            double tol = parityTolerance != null ? parityTolerance : 5.0;
            boolean pass = Math.abs(diffMean) <= tol;
            if (metricName.toLowerCase().contains("utilization") && (fcfsMean > 1.0 + 1e-4 || engineMean > 1.0 + 1e-4)) {
                pass = false;
            }
            parityPassed = pass;
            outcome = pass ? String.format("PASS (parity within ±%.1f)", tol) : String.format("FAIL (drift > ±%.1f or value > 1.0)", tol);
            engineImproved = pass;
        } else {
            // Label by paired 95% CI ONLY
            if (lowerIsBetter) {
                // Favorable direction is negative
                if (ciUpper < 0.0) {
                    outcome = "Improved";
                    engineImproved = true;
                } else if (ciLower > 0.0) {
                    outcome = "Worse";
                    engineImproved = false;
                } else {
                    outcome = "No significant difference";
                    engineImproved = false;
                }
            } else {
                // Favorable direction is positive
                if (ciLower > 0.0) {
                    outcome = "Improved";
                    engineImproved = true;
                } else if (ciUpper < 0.0) {
                    outcome = "Worse";
                    engineImproved = false;
                } else {
                    outcome = "No significant difference";
                    engineImproved = false;
                }
            }
        }

        return MetricSummary.builder()
                .metricName(metricName)
                .fcfsMean(round(fcfsMean))
                .fcfsStd(round(fcfsStd))
                .engineMean(round(engineMean))
                .engineStd(round(engineStd))
                .pairedDiffMean(round(diffMean))
                .pairedDiffStd(round(diffStd))
                .ci95Lower(round(ciLower))
                .ci95Upper(round(ciUpper))
                .relativeChangePercent(round(relChange))
                .outcome(outcome)
                .engineImproved(engineImproved)
                .parityCheck(isParityCheck)
                .parityTolerance(parityTolerance)
                .parityPassed(parityPassed)
                .build();
    }

    private double getTCritical(int df) {
        if (df <= 0) return 1.96;
        if (df < T_CRITICAL_95.length) {
            return T_CRITICAL_95[df];
        }
        return 1.96; // asymptotic normal
    }

    private double mean(double[] arr) {
        double sum = 0.0;
        for (double v : arr) sum += v;
        return sum / arr.length;
    }

    private double sampleStdDev(double[] arr, double mean) {
        if (arr.length <= 1) return 0.0;
        double sumSq = 0.0;
        for (double v : arr) {
            sumSq += Math.pow(v - mean, 2);
        }
        return Math.sqrt(sumSq / (arr.length - 1));
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
