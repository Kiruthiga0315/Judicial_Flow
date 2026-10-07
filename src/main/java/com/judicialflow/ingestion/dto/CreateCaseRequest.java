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
@Schema(description = "Request payload for creating a new case")
public class CreateCaseRequest {

    @NotBlank(message = "Case number is required")
    @Schema(description = "Unique alphanumeric case number identifier", example = "CIV-2024-001092")
    private String caseNumber;

    @NotNull(message = "Case type is required")
    @Schema(description = "Type/Category of the legal case", example = "CIVIL", allowableValues = {"BAIL", "POCSO", "MATRIMONIAL", "CIVIL", "CRIMINAL_OTHER"})
    private CaseType caseType;

    @NotNull(message = "Filing date is required")
    @PastOrPresent(message = "Filing date cannot be in the future")
    @Schema(description = "Date when the case was officially filed", example = "2024-01-15")
    private LocalDate filingDate;

    @Schema(description = "Initial status of the case. Defaults to FILED if omitted.", example = "FILED")
    private CaseStatus currentStatus;

    @Min(value = 0, message = "Prior adjournments cannot be negative")
    @Schema(description = "Number of prior adjournments, defaults to 0", example = "0")
    @Builder.Default
    private int priorAdjournments = 0;

    @Schema(description = "Optional UUID of a parent or related case", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID linkedCaseId;

    @Schema(description = "Optional UUID of the assigned judge", example = "b1ffcd11-9c0b-4ef8-bb6d-6bb9bd380b22")
    private UUID assignedJudgeId;

    @Schema(description = "Optional statutory deadline date", example = "2024-06-30")
    private LocalDate statutoryDeadline;

    @Schema(description = "Optional contact email of the litigant", example = "litigant@example.com")
    private String litigantContactEmail;
}
