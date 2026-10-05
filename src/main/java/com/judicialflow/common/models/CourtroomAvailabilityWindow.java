package com.judicialflow.common.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Courtroom availability window on a given day of the week")
public class CourtroomAvailabilityWindow implements Serializable {

    @NotNull(message = "Day of week is required")
    @Schema(description = "Day of the week", example = "MONDAY")
    private DayOfWeek dayOfWeek;

    @NotNull(message = "Start time is required")
    @JsonFormat(pattern = "HH:mm")
    @Schema(description = "Window start time (HH:mm)", example = "09:00")
    private LocalTime startTime;

    @NotNull(message = "End time is required")
    @JsonFormat(pattern = "HH:mm")
    @Schema(description = "Window end time (HH:mm)", example = "17:30")
    private LocalTime endTime;

    @Schema(description = "Optional notes or details", example = "Available for civil hearings")
    private String notes;
}
