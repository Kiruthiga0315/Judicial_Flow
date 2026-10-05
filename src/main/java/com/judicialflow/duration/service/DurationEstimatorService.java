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

    private final Map<CaseType, SimpleLinearRegression> models = new EnumMap<>(CaseType.class);
    private LocalDateTime lastTrained;

    public void trainModels() {
        log.info("Training duration models on DISPOSED synthetic cases...");
        List<Case> disposedCases = caseRepository.findAll().stream()
                .filter(c -> c.getCurrentStatus() == CaseStatus.DISPOSED && c.getDisposedDate() != null)
                .collect(Collectors.toList());
        
        Map<CaseType, List<Case>> casesByType = disposedCases.stream()
                .collect(Collectors.groupingBy(Case::getCaseType));

        for (CaseType type : CaseType.values()) {
            List<Case> typeCases = casesByType.getOrDefault(type, List.of());
            
            double[] x = new double[typeCases.size()];
            double[] y = new double[typeCases.size()];
            
            for (int i = 0; i < typeCases.size(); i++) {
                Case c = typeCases.get(i);
                // Feature: adjournment count
                x[i] = c.getPriorAdjournments();
                // Target: duration in days (filing to disposal)
                y[i] = ChronoUnit.DAYS.between(c.getFilingDate(), c.getDisposedDate());
            }
            
            SimpleLinearRegression model = new SimpleLinearRegression(x, y);
            models.put(type, model);
            log.info("Trained model for {}: SampleSize={}, Intercept={}, Slope(Adjournments)={}, MAE={}", 
                    type, model.getSampleSize(), model.getIntercept(), model.getSlope(), model.getMae());
        }
        lastTrained = LocalDateTime.now();
    }

    public DurationEstimateResponse estimateForCase(Case courtCase) {
        if (lastTrained == null) {
            trainModels();
        }

        CaseType type = courtCase.getCaseType();
        SimpleLinearRegression model = models.get(type);

        int sampleSize = model != null ? model.getSampleSize() : 0;
        double predictedDays = 0;
        int minDays = 0;
        int maxDays = 0;
        String basis;
        double mae = 0;
        
        if (model != null && sampleSize > 0) {
            double featureValue = courtCase.getPriorAdjournments();
            predictedDays = model.predict(featureValue);
            if (predictedDays < 1) predictedDays = 1;
            
            mae = model.getMae();
            // Provide a range based on Mean Absolute Error
            minDays = (int) Math.max(1, Math.round(predictedDays - mae));
            maxDays = (int) Math.round(predictedDays + mae);
            basis = "based on " + sampleSize + " similar synthetic cases of type " + type;
        } else {
            // Insufficient data fallback
            predictedDays = 180; // default 6 months
            minDays = 90;
            maxDays = 270;
            basis = "insufficient historical data; using generic default";
            mae = 90;
        }

        String topFeatures = "1. CaseType (" + type + "), 2. PriorAdjournments";
        String modelVersion = "LinearRegression-v1.0 (MAE=" + String.format("%.2f", mae) + " days)";

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
