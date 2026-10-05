package com.judicialflow.duration.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DurationEstimateResponse {
    private double predictedDurationDays;
    private int minDurationDays;
    private int maxDurationDays;
    private String basis;
    private String modelVersion;
    private String topFeatures;
    private LocalDateTime computedAt;
}
