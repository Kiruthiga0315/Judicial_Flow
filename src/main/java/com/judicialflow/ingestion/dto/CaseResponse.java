package com.judicialflow.ingestion.dto;

import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.models.Case;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response representation of a case")
public class CaseResponse {

    @Schema(description = "Unique UUID identifier of the case", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Unique case number identifier", example = "CIV-2024-001092")
    private String caseNumber;

    @Schema(description = "Case type/category", example = "CIVIL")
    private CaseType caseType;

    @Schema(description = "Filing date of the case", example = "2024-01-15")
    private LocalDate filingDate;

    @Schema(description = "Current status of the case", example = "PENDING")
    private CaseStatus currentStatus;

    @Schema(description = "Statutory citations", example = "IPC 302, 304B")
    private String citations;

    @Schema(description = "Number of prior adjournments", example = "0")
    private int priorAdjournments;

    @Schema(description = "UUID of the linked case if any")
    private UUID linkedCaseId;

    @Schema(description = "Case number of the linked case if any", example = "CIV-2023-000845")
    private String linkedCaseNumber;

    @Schema(description = "UUID of the assigned judge if any")
    private UUID assignedJudgeId;

    @Schema(description = "Name of the assigned judge if any", example = "Hon. Justice Sharma")
    private String assignedJudgeName;

    @Schema(description = "Whether the case has been soft-deleted", example = "false")
    private boolean deleted;

    @Schema(description = "Timestamp when the case was soft-deleted, if applicable")
    private LocalDateTime deletedAt;

    @Schema(description = "Timestamp when the case was created")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the case was last updated")
    private LocalDateTime updatedAt;

    public static CaseResponse fromEntity(Case c) {
        if (c == null) return null;
        return CaseResponse.builder()
                .id(c.getId())
                .caseNumber(c.getCaseNumber())
                .caseType(c.getCaseType())
                .filingDate(c.getFilingDate())
                .currentStatus(c.getCurrentStatus())
                .citations(c.getCitations())
                .priorAdjournments(c.getPriorAdjournments())
                .linkedCaseId(c.getLinkedCase() != null ? c.getLinkedCase().getId() : null)
                .linkedCaseNumber(c.getLinkedCase() != null ? c.getLinkedCase().getCaseNumber() : null)
                .assignedJudgeId(c.getAssignedJudge() != null ? c.getAssignedJudge().getId() : null)
                .assignedJudgeName(c.getAssignedJudge() != null ? c.getAssignedJudge().getName() : null)
                .deleted(c.isDeleted())
                .deletedAt(c.getDeletedAt())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
