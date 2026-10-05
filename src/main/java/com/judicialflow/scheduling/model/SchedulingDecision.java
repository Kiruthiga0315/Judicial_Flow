package com.judicialflow.scheduling.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "scheduling_decisions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchedulingDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_id", nullable = false)
    private SchedulingProposal proposal;

    @Column(name = "case_number", nullable = false)
    private String caseNumber;

    @Column(name = "chosen_judge_name", nullable = false)
    private String chosenJudgeName;

    @Column(name = "chosen_courtroom_name", nullable = false)
    private String chosenCourtroomName;

    @Column(name = "chosen_time", nullable = false)
    private LocalDateTime chosenTime;

    @Column(name = "constraints_satisfied", nullable = false, columnDefinition = "TEXT")
    private String constraintsSatisfied;

    @Column(name = "runner_up_judge_name")
    private String runnerUpJudgeName;

    @Column(name = "runner_up_courtroom_name")
    private String runnerUpCourtroomName;

    @Column(name = "runner_up_time")
    private LocalDateTime runnerUpTime;

    @Column(name = "runner_up_rejection_reason", columnDefinition = "TEXT")
    private String runnerUpRejectionReason;

    @Column(name = "soft_score_chosen", precision = 10, scale = 4)
    private BigDecimal softScoreChosen;

    @Column(name = "soft_score_runner_up", precision = 10, scale = 4)
    private BigDecimal softScoreRunnerUp;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
