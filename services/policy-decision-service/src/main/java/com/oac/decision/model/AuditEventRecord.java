package com.oac.decision.model;

import java.time.OffsetDateTime;

public record AuditEventRecord(
        String eventId,
        String eventType,
        String entityType,
        String entityId,
        String actor,
        String decisionCode,
        String evidenceRef,
        String severity,
        OffsetDateTime occurredAt
) {
}