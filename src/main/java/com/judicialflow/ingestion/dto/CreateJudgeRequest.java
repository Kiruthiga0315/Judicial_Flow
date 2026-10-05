package com.judicialflow.ingestion.dto;

import com.judicialflow.common.models.JudgeAvailabilityWindow;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
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
@Schema(description = "Request payload for creating a new judge")
public class CreateJudgeRequest {

    @NotBlank(message = "Judge name is required")
    @Schema(description = "Full name of the judge", example = "Hon. Justice Sarah Jenkins")
    private String name;

    @Schema(description = "Legal specialization or division", example = "Commercial & Civil Law")
    private String specialization;

    @Valid
    @Builder.Default
    @Schema(description = "Initial availability windows for hearing sessions")
    private List<JudgeAvailabilityWindow> availabilityWindows = new ArrayList<>();
}
