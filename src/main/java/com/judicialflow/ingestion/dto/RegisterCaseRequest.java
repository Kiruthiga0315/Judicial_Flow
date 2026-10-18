package com.judicialflow.ingestion.dto;

import com.judicialflow.common.enums.CaseType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for a registrar to register a new case")
public class RegisterCaseRequest {

    @NotNull(message = "Case type is required")
    @Schema(description = "Type/Category of the legal case", example = "CIVIL", allowableValues = {"BAIL", "POCSO", "MATRIMONIAL", "CIVIL", "CRIMINAL_OTHER"})
    private CaseType caseType;

    @NotNull(message = "Filing date is required")
    @PastOrPresent(message = "Filing date cannot be in the future")
    @Schema(description = "Date when the case was officially filed", example = "2024-01-15")
    private LocalDate filingDate;

    @Schema(description = "Optional statutory deadline date", example = "2024-06-30")
    private LocalDate statutoryDeadline;

    @Schema(description = "Optional UUIDs of linked cases")
    private List<UUID> linkedCaseIds;

    @Email(message = "Litigant contact email must be a valid email address")
    @Schema(description = "Optional contact email of the litigant", example = "litigant@example.com")
    private String litigantContactEmail;
}
