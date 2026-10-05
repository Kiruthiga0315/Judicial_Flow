package com.judicialflow.ingestion.dto;

import com.judicialflow.common.models.CourtroomAvailabilityWindow;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for creating a new courtroom")
public class CreateCourtroomRequest {

    @NotBlank(message = "Courtroom name is required")
    @Schema(description = "Courtroom name or number", example = "Courtroom 3B")
    private String name;

    @Min(value = 1, message = "Courtroom capacity must be at least 1")
    @Schema(description = "Seating capacity of the courtroom", example = "50")
    private int capacity;

    @Valid
    @Builder.Default
    @Schema(description = "Initial availability windows for hearing sessions")
    private List<CourtroomAvailabilityWindow> availability = new ArrayList<>();
}
