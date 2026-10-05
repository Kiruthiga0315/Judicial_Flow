package com.judicialflow.scheduling.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "scheduling_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchedulingRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Builder.Default
    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status;

    @Builder.Default
    @Column(name = "total_cases_input", nullable = false)
    private int totalCasesInput = 0;

    @Builder.Default
    @Column(name = "total_assigned", nullable = false)
    private int totalAssigned = 0;

    @Builder.Default
    @Column(name = "total_unschedulable", nullable = false)
    private int totalUnschedulable = 0;

    @Builder.Default
    @Column(name = "horizon_days", nullable = false)
    private int horizonDays = 5;

    @Builder.Default
    @Column(name = "default_duration_minutes", nullable = false)
    private int defaultDurationMinutes = 60;

    @Column(name = "seed")
    private Long seed;

    @Column(name = "total_weighted_soft_cost", precision = 12, scale = 4)
    private java.math.BigDecimal totalWeightedSoftCost;

    @Column(name = "cost_breakdown", columnDefinition = "TEXT")
    private String costBreakdown;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
