package com.judicialflow.common.enums;

/**
 * Status values for legal cases aligned with the master brief:
 * - FILED: Case newly ingested into the system awaiting initial hearing scheduling.
 * - PENDING: Historical/backward-compatible synonym for FILED.
 * - SCHEDULED: Case has an approved proposal or committed hearing assigned to a judge/courtroom.
 * - HEARD: Hearing was conducted before the judge.
 * - ADJOURNED: Hearing concluded with an adjournment, placing the case back in the schedulable pool.
 * - DISPOSED: Case reached final judgment or disposal; excluded from further scheduling.
 */
public enum CaseStatus {
    FILED,
    SCHEDULED,
    HEARD,
    ADJOURNED,
    DISPOSED;

    /**
     * Determines whether a case in this status is eligible for scheduling runs.
     */
    public boolean isSchedulable() {
        return this == FILED || this == ADJOURNED;
    }
}
