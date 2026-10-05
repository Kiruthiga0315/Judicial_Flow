package com.judicialflow.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for updating courtroom details")
public class UpdateCourtroomRequest {

    @NotBlank(message = "Courtroom name is required")
    @Schema(description = "Courtroom name or number", example = "Courtroom 3B")
    private String name;

    @Min(value = 1, message = "Courtroom capacity must be at least 1")
    @Schema(description = "Seating capacity of the courtroom", example = "60")
    private int capacity;
}
