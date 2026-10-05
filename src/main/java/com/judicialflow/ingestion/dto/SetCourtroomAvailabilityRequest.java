package com.judicialflow.ingestion.dto;

import com.judicialflow.common.models.CourtroomAvailabilityWindow;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
@Schema(description = "Request payload for updating courtroom capacity and/or availability")
public class SetCourtroomAvailabilityRequest {

    @Min(value = 1, message = "Courtroom capacity must be at least 1")
    @Schema(description = "Updated seating capacity, if updating capacity", example = "75")
    private Integer capacity;

    @Valid
    @Builder.Default
    @Schema(description = "Updated list of availability windows")
    private List<CourtroomAvailabilityWindow> availability = new ArrayList<>();
}
