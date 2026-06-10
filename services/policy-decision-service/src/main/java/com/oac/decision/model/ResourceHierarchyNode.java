package com.oac.decision.model;

/**
 * Represents a node in a resource hierarchy for inherited relationship traversal.
 * Used by the ReBAC graph engine to support parent-child resource relationships
 * (e.g., account -> transaction; organization -> team -> member).
 */
public record ResourceHierarchyNode(
        String resourceId,
        String resourceType,
        String parentId,
        String parentType,
        int depth
) {
}