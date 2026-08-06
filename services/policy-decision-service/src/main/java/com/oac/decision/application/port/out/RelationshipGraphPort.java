package com.oac.decision.application.port.out;

import com.oac.decision.model.CheckPermissionRequest;
import com.oac.decision.model.RelationshipEdge;

import java.util.List;
import java.util.Set;

/**
 * Output port for ReBAC relationship graph operations.
 * Abstracts the underlying storage backend (MongoDB, relational, graph DB) for
 * relationship edges and resource hierarchy traversal.
 */
public interface RelationshipGraphPort {

    /**
     * Find all direct relationships from a subject to a resource.
     */
    List<RelationshipEdge> findRelationships(CheckPermissionRequest request);

    /**
     * Find all resource IDs of a given type that the subject has a specific
     * relationship to (direct or inherited up to maxDepth).
     */
    List<String> findRelatedResourceIds(
            String subjectId,
            String resourceType,
            String relationshipType,
            int maxDepth
    );

    /**
     * Traverse the relationship graph from a subject to discover all reachable
     * resource IDs of a given type, with bounded breadth-first depth.
     * Returns distinct resource IDs reachable through any valid relationship path.
     */
    Set<String> traverseResources(
            String subjectId,
            String resourceType,
            int maxDepth
    );

    /**
     * Create a new relationship edge. Returns the created edge ID.
     */
    String createRelationship(RelationshipEdge edge);

    /**
     * Revoke (soft-delete) a relationship edge by ID.
     */
    void revokeRelationship(String relationshipId);

    /**
     * Check if a relationship exists between subject and resource at any depth
     * up to maxDepth.
     */
    boolean hasRelationship(
            String subjectId,
            String resourceId,
            String relationshipType,
            int maxDepth
    );

    /**
     * Check if a boundary-scoped relationship exists (Section 4.36 composable domains).
     * When {@code boundaryScope} is non-null and non-empty, only edges whose
     * {@code boundaryScope} matches all declared dimensions are traversed.
     */
    boolean hasRelationship(
            String subjectId,
            String resourceId,
            String relationshipType,
            int maxDepth,
            java.util.Map<String, String> boundaryScope
    );

    /** Maximum traversal depth for ReBAC graph queries (configurable). */
    int getMaxTraversalDepth();
}