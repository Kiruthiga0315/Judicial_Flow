package com.judicialflow.ingestion.dto;

import com.judicialflow.common.models.JudgeAvailabilityWindow;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "Request payload for updating judge availability windows")
public class SetJudgeAvailabilityRequest {

    @NotNull(message = "Availability windows list cannot be null")
    @Valid
    @Builder.Default
    @Schema(description = "List of weekly availability windows for the judge")
    private List<JudgeAvailabilityWindow> availabilityWindows = new ArrayList<>();
}
