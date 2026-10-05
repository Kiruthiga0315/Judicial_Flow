package com.judicialflow.ingestion.exceptions;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Details of a field-level validation failure")
public class ValidationError {

    @Schema(description = "Field name that triggered the error", example = "filingDate")
    private String field;

    @Schema(description = "Rejected value", example = "2099-01-01")
    private Object rejectedValue;

    @Schema(description = "Validation failure reason", example = "Filing date cannot be in the future")
    private String message;
}
