package com.judicialflow.scheduling.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optional configuration parameters for triggering a scheduling run.
 * All fields have sensible defaults from application.yml.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchedulingConfigDto {

    /** Number of business days to schedule into. Null = use default from config. */
    private Integer horizonDays;

    /** Default hearing duration in minutes. Null = use default from config. */
    private Integer defaultDurationMinutes;

    /** Seed for local search to guarantee reproducibility. Null = use default (e.g. 42). */
    private Long seed;
}
