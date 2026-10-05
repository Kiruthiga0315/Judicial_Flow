package com.judicialflow.common.models;

import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Case {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String caseNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseType caseType;

    @Column(nullable = false)
    private LocalDate filingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus currentStatus;

    @Column(columnDefinition = "TEXT")
    private String citations;

    @Builder.Default
    @Column(nullable = false)
    private int priorAdjournments = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_case_id")
    private Case linkedCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_judge_id")
    private Judge assignedJudge;

    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
