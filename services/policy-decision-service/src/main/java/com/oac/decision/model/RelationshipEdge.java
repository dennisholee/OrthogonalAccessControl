package com.oac.decision.model;

import java.time.Instant;
import java.util.Map;

/**
 * A ReBAC relationship edge representing a directed relationship from a subject to a resource.
 * Each edge carries a relationship type (e.g., "owner", "reviewer", "member", "manager-of")
 * and is scoped by an optional boundary context for multi-tenant isolation.
 */
public record RelationshipEdge(
        String id,
        String subjectId,
        String subjectType,
        String relationshipType,
        String resourceId,
        String resourceType,
        BoundaryContext boundaryContext,
        Instant validFrom,
        Instant validUntil,
        Map<String, Object> metadata
) {
    public boolean isActive() {
        Instant now = Instant.now();
        return (validFrom == null || !now.isBefore(validFrom))
                && (validUntil == null || now.isBefore(validUntil));
    }
}