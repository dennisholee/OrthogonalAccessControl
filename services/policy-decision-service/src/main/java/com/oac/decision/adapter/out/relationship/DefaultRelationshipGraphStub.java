package com.oac.decision.adapter.out.relationship;

import com.oac.decision.application.port.out.RelationshipGraphPort;
import com.oac.decision.model.CheckPermissionRequest;
import com.oac.decision.model.RelationshipEdge;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Default in-memory stub for RelationshipGraphPort when not using MongoDB profile.
 * Returns empty results for all queries — suitable for development/testing.
 */
@Component
@Profile("!mongodb")
public class DefaultRelationshipGraphStub implements RelationshipGraphPort {

    @Override
    public List<RelationshipEdge> findRelationships(CheckPermissionRequest request) {
        return List.of();
    }

    @Override
    public List<String> findRelatedResourceIds(
            String subjectId, String resourceType, String relationshipType, int maxDepth) {
        return List.of();
    }

    @Override
    public Set<String> traverseResources(String subjectId, String resourceType, int maxDepth) {
        return Set.of();
    }

    @Override
    public String createRelationship(RelationshipEdge edge) {
        return "stub-" + System.identityHashCode(edge);
    }

    @Override
    public void revokeRelationship(String relationshipId) {
        // no-op
    }

    @Override
    public boolean hasRelationship(String subjectId, String resourceId, String relationshipType, int maxDepth) {
        return false;
    }

    @Override
    public int getMaxTraversalDepth() {
        return 3;
    }
}