package com.judicialflow.duration.service;

import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.models.Case;
import com.judicialflow.duration.dto.DurationEstimateResponse;
import com.judicialflow.duration.model.DurationEstimate;
import com.judicialflow.duration.repository.DurationEstimateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DurationEstimatorService {

    private final CaseRepository caseRepository;
    private final DurationEstimateRepository durationEstimateRepository;

    @Value("${duration.estimator.seed:42}")
    private long seed;

    public static final int THRESHOLD_FALLBACK = 8;
    public static final int THRESHOLD_80_20 = 30;

    private final Map<CaseType, ModelStats> models = new EnumMap<>(CaseType.class);
    private LocalDateTime lastTrained;

    public static class ModelStats {
        public SimpleLinearRegression regression;
        public double testMae;
        public double baselineMae;
        public boolean beatsBaseline;
        public int trainingSize;
        public int testSize;
        public String validationMethod;
    }

    public void trainModels() {
        log.info("Training duration models. Seed={}", seed);
        List<Case> disposedCases = caseRepository.findAll().stream()
                .filter(c -> c.getCurrentStatus() == CaseStatus.DISPOSED && c.getDisposedDate() != null)
                .collect(Collectors.toList());
        
        Map<CaseType, List<Case>> casesByType = disposedCases.stream()
                .collect(Collectors.groupingBy(Case::getCaseType));

        for (CaseType type : CaseType.values()) {
            List<Case> typeCases = casesByType.getOrDefault(type, List.of());
            
            if (typeCases.size() < THRESHOLD_FALLBACK) {
                log.warn("Not enough data to train model for {} (count={})", type, typeCases.size());
                continue;
            }

            // Sort by a stable key before shuffling
            typeCases.sort(Comparator.comparing(Case::getId));
            Collections.shuffle(typeCases, new Random(seed));
            
            ModelStats stats = new ModelStats();

            if (typeCases.size() >= THRESHOLD_80_20) {
                // 80/20 split
                int splitIndex = (int) (typeCases.size() * 0.8);
                List<Case> trainSet = typeCases.subList(0, splitIndex);
                List<Case> testSet = typeCases.subList(splitIndex, typeCases.size());
                
                SimpleLinearRegression model = trainOn(trainSet);
                
                // Calculate Test MAE
                double totalTestError = 0;
                double sumTrainY = trainSet.stream().mapToDouble(this::getDurationDays).sum();
                double meanTrainY = sumTrainY / trainSet.size();
                double totalBaselineError = 0;

                for (Case c : testSet) {
                    double actualDays = getDurationDays(c);
                    double predictedDays = model.predict(c.getPriorAdjournments());
                    totalTestError += Math.abs(predictedDays - actualDays);
                    totalBaselineError += Math.abs(meanTrainY - actualDays);
                }
                
                stats.regression = model;
                stats.testMae = totalTestError / testSet.size();
                stats.baselineMae = totalBaselineError / testSet.size();
                stats.trainingSize = trainSet.size();
                stats.testSize = testSet.size();
                stats.validationMethod = "80/20 holdout";

            } else {
                // Leave-One-Out Cross Validation (LOOCV) for 8 to 29 cases
                double totalTestError = 0;
                double totalBaselineError = 0;

                for (int i = 0; i < typeCases.size(); i++) {
                    Case holdout = typeCases.get(i);
                    List<Case> trainSet = typeCases.stream()
                            .filter(c -> !c.getId().equals(holdout.getId()))
                            .collect(Collectors.toList());
                    
                    SimpleLinearRegression model = trainOn(trainSet);
                    
                    double sumTrainY = trainSet.stream().mapToDouble(this::getDurationDays).sum();
                    double meanTrainY = sumTrainY / trainSet.size();
                    
                    double actualDays = getDurationDays(holdout);
                    double predictedDays = model.predict(holdout.getPriorAdjournments());
                    
                    totalTestError += Math.abs(predictedDays - actualDays);
                    totalBaselineError += Math.abs(meanTrainY - actualDays);
                }

                // Final model trained on ALL cases for serving
                stats.regression = trainOn(typeCases);
                stats.testMae = totalTestError / typeCases.size();
                stats.baselineMae = totalBaselineError / typeCases.size();
                stats.trainingSize = typeCases.size() - 1; // Effective training size per fold
                stats.testSize = typeCases.size(); // We validated on all
                stats.validationMethod = "leave-one-out";
            }

            stats.beatsBaseline = stats.testMae < stats.baselineMae;
            models.put(type, stats);
            log.info("Trained model for {}: Method={}, TrainSize={}, TestSize={}, TestMAE={}, BaselineMAE={}, BeatsBaseline={}", 
                    type, stats.validationMethod, stats.trainingSize, stats.testSize, stats.testMae, stats.baselineMae, stats.beatsBaseline);
        }
        lastTrained = LocalDateTime.now();
    }

    private SimpleLinearRegression trainOn(List<Case> trainSet) {
        double[] xTrain = new double[trainSet.size()];
        double[] yTrain = new double[trainSet.size()];
        for (int i = 0; i < trainSet.size(); i++) {
            xTrain[i] = trainSet.get(i).getPriorAdjournments();
            yTrain[i] = getDurationDays(trainSet.get(i));
        }
        return new SimpleLinearRegression(xTrain, yTrain);
    }

    private double getDurationDays(Case c) {
        return ChronoUnit.DAYS.between(c.getFilingDate(), c.getDisposedDate());
    }

    public DurationEstimateResponse estimateForCase(Case courtCase) {
        if (lastTrained == null) {
            trainModels();
        }

        CaseType type = courtCase.getCaseType();
        ModelStats stats = models.get(type);

        double predictedDays;
        double minDays;
        double maxDays;
        String basis;
        double mae;
        double baselineMae = 0;
        boolean beatsBaseline = false;
        
        if (stats != null && stats.regression != null) {
            double featureValue = courtCase.getPriorAdjournments();
            predictedDays = stats.regression.predict(featureValue);
            mae = stats.testMae;
            baselineMae = stats.baselineMae;
            beatsBaseline = stats.beatsBaseline;
            
            basis = "based on " + stats.trainingSize + " training cases and validated on " + stats.testSize + " holdout cases using " + stats.validationMethod + ".";
            if (!beatsBaseline) {
                basis += " Model did not beat the mean-predictor baseline.";
            }
        } else {
            predictedDays = 180;
            mae = 90;
            basis = "fallback, insufficient data; using generic default.";
        }

        // Clamp outputs to minimum of 1 day
        predictedDays = Math.max(1.0, predictedDays);
        minDays = Math.max(1.0, Math.round(predictedDays - mae));
        maxDays = Math.max(1.0, Math.round(predictedDays + mae));

        // Note Leakage disclosure for active cases
        String topFeatures = "CaseType=" + type + ", PriorAdjournments=" + courtCase.getPriorAdjournments() + " (given adjournments so far)";
        String modelVersion = "LinearRegression-v1.2 (Seed=" + seed + ")";

        DurationEstimate estimate = durationEstimateRepository.findByCourtCaseId(courtCase.getId())
                .orElse(DurationEstimate.builder().courtCase(courtCase).build());
        
        estimate.setPredictedDurationDays(predictedDays);
        estimate.setMinDurationDays((int) minDays);
        estimate.setMaxDurationDays((int) maxDays);
        estimate.setBasis(basis);
        estimate.setModelVersion(modelVersion);
        estimate.setTopFeatures(topFeatures);
        estimate.setTestMae(mae);
        estimate.setBaselineMaeDays(baselineMae);
        estimate.setBeatsBaseline(beatsBaseline);
        estimate.setComputedAt(LocalDateTime.now());
        
        durationEstimateRepository.save(estimate);

        return DurationEstimateResponse.builder()
                .predictedDurationDays(predictedDays)
                .minDurationDays((int) minDays)
                .maxDurationDays((int) maxDays)
                .basis(basis)
                .modelVersion(modelVersion)
                .topFeatures(topFeatures)
                .testMae(mae)
                .baselineMaeDays(baselineMae)
                .beatsBaseline(beatsBaseline)
                .computedAt(estimate.getComputedAt())
                .build();
    }
}
