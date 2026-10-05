package com.judicialflow.common.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "judges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Judge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String specialization;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "availability_windows", columnDefinition = "jsonb")
    @Builder.Default
    private List<JudgeAvailabilityWindow> availabilityWindows = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
