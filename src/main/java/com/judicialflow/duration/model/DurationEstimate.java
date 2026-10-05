package com.judicialflow.duration.model;

import com.judicialflow.common.models.Case;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "duration_estimates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DurationEstimate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Case courtCase;

    @Column(name = "predicted_duration_days", nullable = false)
    private double predictedDurationDays;

    @Column(name = "min_duration_days", nullable = false)
    private int minDurationDays;

    @Column(name = "max_duration_days", nullable = false)
    private int maxDurationDays;

    @Column(nullable = false)
    private String basis;

    @Column(name = "model_version", nullable = false)
    private String modelVersion;

    @Column(name = "top_features")
    private String topFeatures;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
