package com.judicialflow.ingestion.dto;

import com.judicialflow.common.models.Courtroom;
import com.judicialflow.common.models.CourtroomAvailabilityWindow;
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
@Schema(description = "Response representation of a courtroom")
public class CourtroomResponse {

    @Schema(description = "Unique UUID identifier of the courtroom", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
    private UUID id;

    @Schema(description = "Courtroom name or number", example = "Courtroom 3B")
    private String name;

    @Schema(description = "Seating capacity", example = "50")
    private int capacity;

    @Schema(description = "Configured availability windows")
    private List<CourtroomAvailabilityWindow> availability;

    @Schema(description = "Timestamp when the courtroom was created")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the courtroom was last updated")
    private LocalDateTime updatedAt;

    public static CourtroomResponse fromEntity(Courtroom courtroom) {
        if (courtroom == null) return null;
        return CourtroomResponse.builder()
                .id(courtroom.getId())
                .name(courtroom.getName())
                .capacity(courtroom.getCapacity())
                .availability(courtroom.getAvailability())
                .createdAt(courtroom.getCreatedAt())
                .updatedAt(courtroom.getUpdatedAt())
                .build();
    }
}
