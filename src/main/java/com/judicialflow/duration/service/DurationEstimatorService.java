package com.judicialflow.duration.service;

import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.models.Case;
import com.judicialflow.duration.dto.DurationEstimateResponse;
import com.judicialflow.duration.model.DurationEstimate;
import com.judicialflow.duration.repository.DurationEstimateRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DurationEstimatorService {

    private final CaseRepository caseRepository;
    private final DurationEstimateRepository durationEstimateRepository;

    private final Map<CaseType, ModelStats> models = new EnumMap<>(CaseType.class);
    private LocalDateTime lastTrained;

    public static class ModelStats {
        public SimpleLinearRegression regression;
        public double testMae;
        public int trainingSize;
        public int testSize;
    }

    public void trainModels() {
        log.info("Training duration models on DISPOSED synthetic cases with 80/20 split...");
        List<Case> disposedCases = caseRepository.findAll().stream()
                .filter(c -> c.getCurrentStatus() == CaseStatus.DISPOSED && c.getDisposedDate() != null)
                .collect(Collectors.toList());
        
        Map<CaseType, List<Case>> casesByType = disposedCases.stream()
                .collect(Collectors.groupingBy(Case::getCaseType));

        for (CaseType type : CaseType.values()) {
            List<Case> typeCases = casesByType.getOrDefault(type, List.of());
            
            if (typeCases.size() < 5) {
                log.warn("Not enough data to train/test model for {}", type);
                continue;
            }

            // Shuffle for random split
            Collections.shuffle(typeCases);
            
            // 80/20 split
            int splitIndex = (int) (typeCases.size() * 0.8);
            List<Case> trainSet = typeCases.subList(0, splitIndex);
            List<Case> testSet = typeCases.subList(splitIndex, typeCases.size());
            
            double[] xTrain = new double[trainSet.size()];
            double[] yTrain = new double[trainSet.size()];
            for (int i = 0; i < trainSet.size(); i++) {
                xTrain[i] = trainSet.get(i).getPriorAdjournments();
                yTrain[i] = ChronoUnit.DAYS.between(trainSet.get(i).getFilingDate(), trainSet.get(i).getDisposedDate());
            }
            
            SimpleLinearRegression model = new SimpleLinearRegression(xTrain, yTrain);
            
            // Calculate Test MAE
            double totalTestError = 0;
            for (Case c : testSet) {
                double actualDays = ChronoUnit.DAYS.between(c.getFilingDate(), c.getDisposedDate());
                double predictedDays = model.predict(c.getPriorAdjournments());
                totalTestError += Math.abs(predictedDays - actualDays);
            }
            double testMae = totalTestError / testSet.size();
            
            ModelStats stats = new ModelStats();
            stats.regression = model;
            stats.testMae = testMae;
            stats.trainingSize = trainSet.size();
            stats.testSize = testSet.size();
            
            models.put(type, stats);
            log.info("Trained model for {}: TrainSize={}, TestSize={}, TestMAE={}", 
                    type, stats.trainingSize, stats.testSize, stats.testMae);
        }
        lastTrained = LocalDateTime.now();
    }

    public DurationEstimateResponse estimateForCase(Case courtCase) {
        if (lastTrained == null) {
            trainModels();
        }

        CaseType type = courtCase.getCaseType();
        ModelStats stats = models.get(type);

        double predictedDays = 0;
        int minDays = 0;
        int maxDays = 0;
        String basis;
        double mae = 0;
        
        if (stats != null && stats.regression != null) {
            double featureValue = courtCase.getPriorAdjournments();
            predictedDays = stats.regression.predict(featureValue);
            if (predictedDays < 1) predictedDays = 1;
            
            mae = stats.testMae;
            minDays = (int) Math.max(1, Math.round(predictedDays - mae));
            maxDays = (int) Math.round(predictedDays + mae);
            basis = "based on " + stats.trainingSize + " training cases and validated on " + stats.testSize + " holdout cases";
        } else {
            predictedDays = 180;
            minDays = 90;
            maxDays = 270;
            basis = "insufficient historical data; using generic default";
            mae = 90;
        }

        String topFeatures = "CaseType=" + type + ", PriorAdjournments=" + courtCase.getPriorAdjournments();
        String modelVersion = "LinearRegression-v1.1 (TestMAE=" + String.format("%.2f", mae) + " days)";

        DurationEstimate estimate = durationEstimateRepository.findByCourtCaseId(courtCase.getId())
                .orElse(DurationEstimate.builder().courtCase(courtCase).build());
        
        estimate.setPredictedDurationDays(predictedDays);
        estimate.setMinDurationDays(minDays);
        estimate.setMaxDurationDays(maxDays);
        estimate.setBasis(basis);
        estimate.setModelVersion(modelVersion);
        estimate.setTopFeatures(topFeatures);
        estimate.setComputedAt(LocalDateTime.now());
        
        durationEstimateRepository.save(estimate);

        return DurationEstimateResponse.builder()
                .predictedDurationDays(predictedDays)
                .minDurationDays(minDays)
                .maxDurationDays(maxDays)
                .basis(basis)
                .modelVersion(modelVersion)
                .topFeatures(topFeatures)
                .computedAt(estimate.getComputedAt())
                .build();
    }
}
