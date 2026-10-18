package com.judicialflow.ingestion.dto;

import com.judicialflow.common.models.Case;
import com.judicialflow.priority.dto.ScoreFactorBreakdown;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RegisterCaseResponse {
    private CaseResponse caseDetails;
    private double initialScore;
    private List<ScoreFactorBreakdown> scoreBreakdown;
}
