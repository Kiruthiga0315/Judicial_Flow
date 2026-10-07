package com.judicialflow.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditFilterCriteria {
    private String actor;
    private String action;
    private String entityType;
    private String entityId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private java.time.Instant from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private java.time.Instant to;
}
