package com.judicialflow.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for updating judge details")
public class UpdateJudgeRequest {

    @NotBlank(message = "Judge name is required")
    @Schema(description = "Full name of the judge", example = "Hon. Justice Sarah Jenkins")
    private String name;

    @Schema(description = "Legal specialization or division", example = "Appellate & Constitutional")
    private String specialization;
}
