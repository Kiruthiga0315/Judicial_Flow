package com.judicialflow.ingestion.exceptions;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Standard structured error response")
public class ErrorResponse {

    @Builder.Default
    @Schema(description = "Timestamp when the error occurred", example = "2026-09-13T21:45:00")
    private LocalDateTime timestamp = LocalDateTime.now();

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "HTTP error title", example = "Bad Request")
    private String error;

    @Schema(description = "Error description message", example = "Validation failed for one or more fields")
    private String message;

    @Schema(description = "Request URI path", example = "/api/cases")
    private String path;

    @Builder.Default
    @Schema(description = "List of validation errors if applicable")
    private List<ValidationError> errors = new ArrayList<>();

    @Schema(description = "ID of running run if conflict occurred")
    private String runningRunId;
}
