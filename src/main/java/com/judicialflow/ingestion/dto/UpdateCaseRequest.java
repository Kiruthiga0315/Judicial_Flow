package com.judicialflow.ingestion.dto;

import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
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
@Schema(description = "Request payload for updating an existing case")
public class UpdateCaseRequest {

    @NotBlank(message = "Case number is required")
    @Schema(description = "Unique alphanumeric case number identifier", example = "CIV-2024-001092")
    private String caseNumber;

    @NotNull(message = "Case type is required")
    @Schema(description = "Type/Category of the legal case", example = "CIVIL")
    private CaseType caseType;

    @NotNull(message = "Filing date is required")
    @PastOrPresent(message = "Filing date cannot be in the future")
    @Schema(description = "Date when the case was officially filed", example = "2024-01-15")
    private LocalDate filingDate;

    @NotNull(message = "Current status is required")
    @Schema(description = "Current status of the case", example = "SCHEDULED")
    private CaseStatus currentStatus;

    @Min(value = 0, message = "Prior adjournments cannot be negative")
    @Schema(description = "Number of prior adjournments", example = "1")
    private int priorAdjournments;

    @Schema(description = "Optional UUID of a parent or related case", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID linkedCaseId;

    @Schema(description = "Optional UUID of the assigned judge", example = "b1ffcd11-9c0b-4ef8-bb6d-6bb9bd380b22")
    private UUID assignedJudgeId;

    @Schema(description = "Optional statutory deadline date", example = "2024-06-30")
    private LocalDate statutoryDeadline;

    @Schema(description = "Optional contact email of the litigant", example = "litigant@example.com")
    private String litigantContactEmail;
}
