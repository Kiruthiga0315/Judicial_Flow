package com.judicialflow.ingestion.dto;

import com.judicialflow.common.models.Judge;
import com.judicialflow.common.models.JudgeAvailabilityWindow;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response representation of a judge")
public class JudgeResponse {

    @Schema(description = "Unique UUID identifier of the judge", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID id;

    @Schema(description = "Full name of the judge", example = "Hon. Justice Sarah Jenkins")
    private String name;

    @Schema(description = "Legal specialization or division", example = "Commercial & Civil Law")
    private String specialization;

    @Schema(description = "Configured availability windows for hearings")
    private List<JudgeAvailabilityWindow> availabilityWindows;

    @Schema(description = "Timestamp when the judge record was created")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the judge record was last updated")
    private LocalDateTime updatedAt;

    public static JudgeResponse fromEntity(Judge judge) {
        if (judge == null) return null;
        return JudgeResponse.builder()
                .id(judge.getId())
                .name(judge.getName())
                .specialization(judge.getSpecialization())
                .availabilityWindows(judge.getAvailabilityWindows())
                .createdAt(judge.getCreatedAt())
                .updatedAt(judge.getUpdatedAt())
                .build();
    }
}
