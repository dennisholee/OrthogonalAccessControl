package com.oac.decision.model;

import java.time.OffsetDateTime;

/**
 * Append-only consent event record (docs/POLICY_ARCHITECTURE.md Section 4.19).
 * <p>
 * Every consent state transition (granted, withdrawn, expired, reinstated, renewal,
 * objection) MUST be appended to the consent-events log with a monotonic version
 * number and timestamp, enabling historical audit replay.
 */
public record ConsentEvent(
        String subjectId,
        String attributeName,
        String eventType,
        String fromStatus,
        String toStatus,
        long consentVersion,
        OffsetDateTime timestamp,
        String actor
) {
    public ConsentEvent {
        timestamp = timestamp == null ? OffsetDateTime.now() : timestamp;
    }

    /** Event type constants matching POLICY_ARCHITECTURE.md Section 4.19. */
    public static final String EVENT_GRANTED = "GRANTED";
    public static final String EVENT_WITHDRAWN = "WITHDRAWN";
    public static final String EVENT_EXPIRED = "EXPIRED";
    public static final String EVENT_RENEWED = "RENEWED";
    public static final String EVENT_OBJECTION = "OBJECTION";
    public static final String EVENT_REINSTATED = "REINSTATED";
}