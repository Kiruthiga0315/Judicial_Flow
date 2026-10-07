package com.judicialflow.duration.dto;

import com.judicialflow.common.enums.CaseType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModelEvaluationResponse {
    private CaseType caseType;
    private double testMae;
    private double baselineMae;
    private boolean beatsBaseline;
    private int trainingSampleCount;
    private int testSampleSize;
    private String validationMethod;
    private boolean usingBaseline;
    private double baselineMeanDays;
}
