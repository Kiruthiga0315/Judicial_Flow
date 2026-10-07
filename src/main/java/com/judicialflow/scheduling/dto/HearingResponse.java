package com.judicialflow.scheduling.dto;

import com.judicialflow.common.enums.HearingStatus;
import com.judicialflow.common.models.Hearing;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HearingResponse {

    @Component
    public static class HearingResponseCourtZoneConfig {
        public HearingResponseCourtZoneConfig(@Value("${court.zone-id:Asia/Kolkata}") String zoneId) {
            HearingResponse.setCourtZone(ZoneId.of(zoneId));
        }
    }

    private static volatile ZoneId courtZone = ZoneId.of("Asia/Kolkata");

    public static ZoneId getCourtZone() {
        return courtZone;
    }

    public static void setCourtZone(ZoneId zone) {
        courtZone = zone;
    }

    private UUID id;
    private UUID caseId;
    private String caseNumber;
    private UUID judgeId;
    private String judgeName;
    private UUID courtroomId;
    private String courtroomName;
    private OffsetDateTime scheduledTime;
    private int durationMinutes;
    private HearingStatus status;

    public static HearingResponse fromEntity(Hearing hearing) {
        if (hearing == null) return null;
        return HearingResponse.builder()
                .id(hearing.getId())
                .caseId(hearing.getLegalCase() != null ? hearing.getLegalCase().getId() : null)
                .caseNumber(hearing.getLegalCase() != null ? hearing.getLegalCase().getCaseNumber() : null)
                .judgeId(hearing.getJudge() != null ? hearing.getJudge().getId() : null)
                .judgeName(hearing.getJudge() != null ? hearing.getJudge().getName() : null)
                .courtroomId(hearing.getCourtroom() != null ? hearing.getCourtroom().getId() : null)
                .courtroomName(hearing.getCourtroom() != null ? hearing.getCourtroom().getName() : null)
                .scheduledTime(hearing.getScheduledTime() != null ? hearing.getScheduledTime().atZone(courtZone).toOffsetDateTime() : null)
                .durationMinutes(hearing.getEstimatedDurationMinutes())
                .status(hearing.getStatus())
                .build();
    }
}
