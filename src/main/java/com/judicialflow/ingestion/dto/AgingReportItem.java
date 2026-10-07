package com.judicialflow.ingestion.dto;

import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgingReportItem {
    private UUID caseId;
    private String caseNumber;
    private CaseType caseType;
    private CaseStatus status;
    private LocalDate filingDate;
    private long daysPending;
    private int adjournments;
    private LocalDate statutoryDeadline;
    private Long daysToDeadline;
    private Double priorityScore;
    private String assignedJudgeName;
    private String assignedCourtroomName;
}
