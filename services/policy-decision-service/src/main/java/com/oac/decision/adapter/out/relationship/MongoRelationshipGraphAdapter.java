package com.oac.decision.adapter.out.relationship;

import com.oac.decision.application.port.out.RelationshipGraphPort;
import com.oac.decision.model.BoundaryContext;
import com.oac.decision.model.CheckPermissionRequest;
import com.oac.decision.model.RelationshipEdge;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MongoDB-backed ReBAC relationship graph adapter.
 * Implements bounded BFS traversal up to configurable max depth (default 3 hops)
 * for relationship graph queries, with boundary context filtering.
 */
@Component
@Profile("mongodb")
public class MongoRelationshipGraphAdapter implements RelationshipGraphPort {

    private static final int DEFAULT_MAX_DEPTH = 3;
    private static final String COLLECTION = "relationships";

    private final MongoTemplate mongoTemplate;

    public MongoRelationshipGraphAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<RelationshipEdge> findRelationships(CheckPermissionRequest request) {
        Criteria criteria = Criteria.where("subjectId").is(request.subject().id())
                .and("resourceId").is(request.resource().id())
                .and("resourceType").is(request.resource().type())
                .andOperator(
                        Criteria.where("validFrom").lte(Instant.now()),
                        new Criteria().orOperator(
                                Criteria.where("validUntil").exists(false),
                                Criteria.where("validUntil").isNull(),
                                Criteria.where("validUntil").gt(Instant.now())
                        )
                );

        if (request.boundaryContext() != null) {
            criteria = applyBoundaryFilter(criteria, request.boundaryContext());
        }

        return mongoTemplate.find(Query.query(criteria), RelationshipEdge.class, COLLECTION);
    }

    @Override
    public List<String> findRelatedResourceIds(
            String subjectId,
            String resourceType,
            String relationshipType,
            int maxDepth
    ) {
        if (maxDepth <= 0) maxDepth = DEFAULT_MAX_DEPTH;

        Set<String> visited = new HashSet<>();
        Deque<BfsNode> queue = new ArrayDeque<>();
        Set<String> result = new HashSet<>();

        // Level 0: direct relationships from subject
        queue.add(new BfsNode(subjectId, "subject", 0));

        while (!queue.isEmpty()) {
            BfsNode current = queue.pollFirst();
            if (current.depth > maxDepth) continue;

            String nodeKey = current.nodeType + ":" + current.nodeId;
            if (!visited.add(nodeKey)) continue;

            if (current.depth > 0 && "resource".equals(current.nodeType)) {
                result.add(current.nodeId);
                continue;
            }

            // Find outgoing edges from this node
            Criteria edgeCriteria;
            if ("subject".equals(current.nodeType)) {
                edgeCriteria = Criteria.where("subjectId").is(current.nodeId);
            } else {
                edgeCriteria = Criteria.where("resourceId").is(current.nodeId);
            }
            if (relationshipType != null) {
                edgeCriteria = edgeCriteria.and("relationshipType").is(relationshipType);
            }
            if (resourceType != null) {
                edgeCriteria = edgeCriteria.and("resourceType").is(resourceType);
            }
            edgeCriteria = edgeCriteria.andOperator(
                    Criteria.where("validFrom").lte(Instant.now()),
                    new Criteria().orOperator(
                            Criteria.where("validUntil").exists(false),
                            Criteria.where("validUntil").isNull(),
                            Criteria.where("validUntil").gt(Instant.now())
                    )
            );

            List<RelationshipEdge> edges = mongoTemplate.find(
                    Query.query(edgeCriteria), RelationshipEdge.class, COLLECTION);

            for (RelationshipEdge edge : edges) {
                if ("subject".equals(current.nodeType)) {
                    // subject -> resource
                    queue.add(new BfsNode(edge.resourceId(), "resource", current.depth + 1));
                } else {
                    // resource -> subject (reverse direction for inherited relationships)
                    queue.add(new BfsNode(edge.subjectId(), "subject", current.depth + 1));
                }
            }
        }

        return new ArrayList<>(result);
    }

    @Override
    public Set<String> traverseResources(
            String subjectId,
            String resourceType,
            int maxDepth
    ) {
        if (maxDepth <= 0) maxDepth = DEFAULT_MAX_DEPTH;

        Set<String> visited = new HashSet<>();
        Deque<BfsNode> queue = new ArrayDeque<>();
        Set<String> result = new HashSet<>();

        queue.add(new BfsNode(subjectId, "subject", 0));

        while (!queue.isEmpty()) {
            BfsNode current = queue.pollFirst();
            if (current.depth > maxDepth) continue;

            String nodeKey = current.nodeType + ":" + current.nodeId;
            if (!visited.add(nodeKey)) continue;

            if (current.depth > 0 && "resource".equals(current.nodeType)) {
                result.add(current.nodeId);
                if (current.depth < maxDepth) {
                    // Check for further traversal through this resource
                    queue.add(new BfsNode(current.nodeId, "resource_continue", current.depth));
                }
                continue;
            }

            Criteria edgeCriteria;
            if ("subject".equals(current.nodeType) || "resource".equals(current.nodeType)) {
                edgeCriteria = Criteria.where("subjectId").is(current.nodeId);
            } else {
                edgeCriteria = new Criteria().orOperator(
                        Criteria.where("resourceId").is(current.nodeId),
                        Criteria.where("subjectId").is(current.nodeId)
                );
            }
            if (resourceType != null) {
                edgeCriteria = edgeCriteria.and("resourceType").is(resourceType);
            }
            edgeCriteria = edgeCriteria.andOperator(
                    Criteria.where("validFrom").lte(Instant.now()),
                    new Criteria().orOperator(
                            Criteria.where("validUntil").exists(false),
                            Criteria.where("validUntil").isNull(),
                            Criteria.where("validUntil").gt(Instant.now())
                    )
            );

            List<RelationshipEdge> edges = mongoTemplate.find(
                    Query.query(edgeCriteria), RelationshipEdge.class, COLLECTION);

            for (RelationshipEdge edge : edges) {
                queue.add(new BfsNode(edge.resourceId(), "resource", current.depth + 1));
            }
        }

        return result;
    }

    @Override
    public String createRelationship(RelationshipEdge edge) {
        RelationshipEdge saved = mongoTemplate.save(edge, COLLECTION);
        return saved.id();
    }

    @Override
    public void revokeRelationship(String relationshipId) {
        mongoTemplate.remove(Query.query(Criteria.where("id").is(relationshipId)), COLLECTION);
    }

    @Override
    public boolean hasRelationship(
            String subjectId,
            String resourceId,
            String relationshipType,
            int maxDepth
    ) {
        if (maxDepth <= 0) maxDepth = DEFAULT_MAX_DEPTH;

        Set<String> visited = new HashSet<>();
        Deque<BfsNode> queue = new ArrayDeque<>();
        queue.add(new BfsNode(subjectId, "subject", 0));

        while (!queue.isEmpty()) {
            BfsNode current = queue.pollFirst();
            if (current.depth > maxDepth) continue;

            String nodeKey = current.nodeType + ":" + current.nodeId;
            if (!visited.add(nodeKey)) continue;

            // Check direct relationship at this node
            Criteria directCheck = Criteria.where("subjectId").is(current.nodeId)
                    .and("resourceId").is(resourceId);
            if (relationshipType != null) {
                directCheck = directCheck.and("relationshipType").is(relationshipType);
            }
            directCheck = directCheck.andOperator(
                    Criteria.where("validFrom").lte(Instant.now()),
                    new Criteria().orOperator(
                            Criteria.where("validUntil").exists(false),
                            Criteria.where("validUntil").isNull(),
                            Criteria.where("validUntil").gt(Instant.now())
                    )
            );

            long count = mongoTemplate.count(
                    Query.query(directCheck), RelationshipEdge.class, COLLECTION);
            if (count > 0) return true;

            if (current.depth < maxDepth) {
                // Traverse further
                Criteria edgeCriteria = Criteria.where("subjectId").is(current.nodeId);
                edgeCriteria = edgeCriteria.andOperator(
                        Criteria.where("validFrom").lte(Instant.now()),
                        new Criteria().orOperator(
                                Criteria.where("validUntil").exists(false),
                                Criteria.where("validUntil").isNull(),
                                Criteria.where("validUntil").gt(Instant.now())
                        )
                );

                List<RelationshipEdge> edges = mongoTemplate.find(
                        Query.query(edgeCriteria), RelationshipEdge.class, COLLECTION);
                for (RelationshipEdge edge : edges) {
                    queue.add(new BfsNode(edge.resourceId(), "resource", current.depth + 1));
                }
            }
        }

        return false;
    }

    @Override
    public int getMaxTraversalDepth() {
        return DEFAULT_MAX_DEPTH;
    }

    private Criteria applyBoundaryFilter(Criteria criteria, BoundaryContext boundary) {
        List<Criteria> boundaryCriteria = new ArrayList<>();
        if (boundary.tenant() != null) boundaryCriteria.add(Criteria.where("boundaryContext.tenant").is(boundary.tenant()));
        if (boundary.geography() != null) boundaryCriteria.add(Criteria.where("boundaryContext.geography").is(boundary.geography()));
        if (boundary.market() != null) boundaryCriteria.add(Criteria.where("boundaryContext.market").is(boundary.market()));
        if (boundary.lineOfBusiness() != null) boundaryCriteria.add(Criteria.where("boundaryContext.lineOfBusiness").is(boundary.lineOfBusiness()));
        if (boundary.channel() != null) boundaryCriteria.add(Criteria.where("boundaryContext.channel").is(boundary.channel()));
        if (!boundaryCriteria.isEmpty()) {
            criteria = criteria.andOperator(boundaryCriteria.toArray(new Criteria[0]));
        }
        return criteria;
    }

    private record BfsNode(String nodeId, String nodeType, int depth) {}
}