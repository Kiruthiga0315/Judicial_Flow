package com.judicialflow.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response confirming registered judge leave and impacted hearings")
public class JudgeLeaveResponse {

    @Schema(description = "Judge leave unique ID")
    private UUID id;

    @Schema(description = "Judge ID")
    private UUID judgeId;

    @Schema(description = "Judge name")
    private String judgeName;

    @Schema(description = "Leave start date")
    private LocalDate startDate;

    @Schema(description = "Leave end date")
    private LocalDate endDate;

    @Schema(description = "Reason for leave")
    private String reason;

    @Schema(description = "Count of committed hearings that were adjourned/cancelled due to this leave")
    private int affectedHearingsCount;

    @Schema(description = "Case numbers of matters returned to the schedulable pool")
    private List<String> affectedCaseNumbers;

    @Schema(description = "Informational summary message")
    private String message;

    @Schema(description = "Timestamp when leave was registered")
    private LocalDateTime createdAt;
}
