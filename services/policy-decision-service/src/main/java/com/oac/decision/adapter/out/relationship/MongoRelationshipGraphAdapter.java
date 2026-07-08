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
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * MongoDB-backed ReBAC relationship graph adapter.
 * Implements bounded BFS traversal using raw Map documents.
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
        List<Map> docs = mongoTemplate.find(Query.query(
                Criteria.where("subjectId").is(request.subject().id())
                        .and("resourceId").is(request.resource().id())
        ), Map.class, COLLECTION);
        return docs.stream().map(this::toEdge).filter(RelationshipEdge::isActive).toList();
    }

    @Override
    public List<String> findRelatedResourceIds(
            String subjectId, String resourceType, String relationshipType, int maxDepth) {
        if (maxDepth <= 0) maxDepth = DEFAULT_MAX_DEPTH;
        Set<String> visited = new HashSet<>();
        Deque<BfsNode> queue = new ArrayDeque<>();
        Set<String> result = new HashSet<>();
        queue.add(new BfsNode(subjectId, "subject", 0));
        while (!queue.isEmpty()) {
            BfsNode current = queue.pollFirst();
            if (current.depth > maxDepth) continue;
            if (!visited.add(current.nodeType + ":" + current.nodeId)) continue;
            if (current.depth > 0 && "resource".equals(current.nodeType)) {
                result.add(current.nodeId);
                continue;
            }
            for (Map edge : findEdges(current.nodeId, current.nodeType, relationshipType, resourceType)) {
                if (isExpired(edge)) continue;
                if ("subject".equals(current.nodeType))
                    queue.add(new BfsNode(str(edge, "resourceId"), "resource", current.depth + 1));
                else
                    queue.add(new BfsNode(str(edge, "subjectId"), "subject", current.depth + 1));
            }
        }
        return new ArrayList<>(result);
    }

    @Override
    public Set<String> traverseResources(String subjectId, String resourceType, int maxDepth) {
        if (maxDepth <= 0) maxDepth = DEFAULT_MAX_DEPTH;
        Set<String> visited = new HashSet<>();
        Deque<BfsNode> queue = new ArrayDeque<>();
        Set<String> result = new HashSet<>();
        queue.add(new BfsNode(subjectId, "subject", 0));
        while (!queue.isEmpty()) {
            BfsNode current = queue.pollFirst();
            if (current.depth > maxDepth) continue;
            if (!visited.add(current.nodeType + ":" + current.nodeId)) continue;
            if (current.depth > 0 && "resource".equals(current.nodeType)) {
                result.add(current.nodeId);
                if (current.depth < maxDepth)
                    queue.add(new BfsNode(current.nodeId, "resource_continue", current.depth));
                continue;
            }
            Criteria c = "subject".equals(current.nodeType)
                    ? Criteria.where("subjectId").is(current.nodeId)
                    : Criteria.where("resourceId").is(current.nodeId);
            if (resourceType != null) c = c.and("resourceType").is(resourceType);
            for (Map edge : mongoTemplate.find(Query.query(c), Map.class, COLLECTION)) {
                if (isExpired(edge)) continue;
                if ("subject".equals(current.nodeType))
                    queue.add(new BfsNode(str(edge, "resourceId"), "resource", current.depth + 1));
                else
                    queue.add(new BfsNode(str(edge, "subjectId"), "subject", current.depth + 1));
            }
        }
        return result;
    }

    @Override
    public boolean hasRelationship(String subjectId, String resourceId, String relationshipType, int maxDepth) {
        if (maxDepth <= 0) maxDepth = DEFAULT_MAX_DEPTH;
        Set<String> visited = new HashSet<>();
        Deque<BfsNode> queue = new ArrayDeque<>();
        queue.add(new BfsNode(subjectId, "subject", 0));

        while (!queue.isEmpty()) {
            BfsNode current = queue.pollFirst();
            if (current.depth > maxDepth) continue;
            if (!visited.add(current.nodeType + ":" + current.nodeId)) continue;

            // Direct forward: subjectId=current → resourceId=target
            // When relationshipType is provided, apply it to the direct edge check
            // from the original source (depth=0). For intermediate hops (depth>0),
            // DON'T apply the type filter because the intermediate edges may have
            // different types (e.g., chain: bob→alice:manages, alice→ORD-789:owner).
            String effectiveType = (current.depth == 0) ? relationshipType : null;
            Criteria directCheck = Criteria.where("subjectId").is(current.nodeId)
                    .and("resourceId").is(resourceId);
            if (effectiveType != null) {
                directCheck = directCheck.and("relationshipType").is(effectiveType);
            }
            for (Map edge : mongoTemplate.find(Query.query(directCheck), Map.class, COLLECTION)) {
                if (!isExpired(edge)) return true;
            }

            // Reverse: subjectId=target → resourceId=current (for depth>0)
            if (current.depth > 0) {
                Criteria reverseCheck = Criteria.where("subjectId").is(resourceId)
                        .and("resourceId").is(current.nodeId);
                if (effectiveType != null) {
                    reverseCheck = reverseCheck.and("relationshipType").is(effectiveType);
                }
                for (Map edge : mongoTemplate.find(Query.query(reverseCheck), Map.class, COLLECTION)) {
                    if (!isExpired(edge)) return true;
                }
            }

            if (current.depth >= maxDepth) continue;

            // Forward edges (subject → resource)
            for (Map edge : mongoTemplate.find(Query.query(
                    Criteria.where("subjectId").is(current.nodeId)), Map.class, COLLECTION)) {
                if (isExpired(edge)) continue;
                queue.add(new BfsNode(str(edge, "resourceId"), "resource", current.depth + 1));
            }

            // Reverse edges (resource ← subject)
            for (Map edge : mongoTemplate.find(Query.query(
                    Criteria.where("resourceId").is(current.nodeId)), Map.class, COLLECTION)) {
                if (isExpired(edge)) continue;
                String otherType = "subject".equals(current.nodeType) ? "resource" : "subject";
                String otherId = "subject".equals(current.nodeType)
                        ? str(edge, "resourceId") : str(edge, "subjectId");
                queue.add(new BfsNode(otherId, otherType, current.depth + 1));
            }
        }
        return false;
    }

    /** Check if a Map-based edge document has expired. */
    private boolean isExpired(Map edge) {
        Object exp = edge.get("expiresAt");
        if (exp == null) return false;
        try { return Instant.now().isAfter(Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(exp.toString()))); }
        catch (Exception e) { return false; }
    }

    @Override
    public String createRelationship(RelationshipEdge edge) {
        var saved = mongoTemplate.insert(edge, COLLECTION);
        org.bson.Document q = mongoTemplate.getCollection(COLLECTION).find()
                .sort(new org.bson.Document("_id", -1)).first();
        return q != null ? q.getObjectId("_id").toHexString() : UUID.randomUUID().toString();
    }

    @Override
    public void revokeRelationship(String relationshipId) {
        mongoTemplate.remove(Query.query(Criteria.where("_id").is(new org.bson.types.ObjectId(relationshipId))), COLLECTION);
    }

    @Override
    public int getMaxTraversalDepth() { return DEFAULT_MAX_DEPTH; }

    private List<Map> findEdges(String nodeId, String nodeType, String relationshipType, String resourceType) {
        Criteria c = "subject".equals(nodeType)
                ? Criteria.where("subjectId").is(nodeId)
                : Criteria.where("resourceId").is(nodeId);
        if (relationshipType != null) c = c.and("relationshipType").is(relationshipType);
        if (resourceType != null) c = c.and("resourceType").is(resourceType);
        return mongoTemplate.find(Query.query(c), Map.class, COLLECTION);
    }

    private RelationshipEdge toEdge(Map doc) {
        return new RelationshipEdge(str(doc, "id"), str(doc, "subjectId"), str(doc, "subjectType"),
                str(doc, "relationshipType"), str(doc, "resourceId"), str(doc, "resourceType"),
                null, parseInstant(doc, "validFrom"), parseInstant(doc, "validUntil"), null);
    }

    private String str(Map doc, String key) { Object v = doc.get(key); return v == null ? null : v.toString(); }
    private Instant parseInstant(Map doc, String key) {
        Object v = doc.get(key);
        if (v == null) return null;
        if (v instanceof Instant i) return i;
        try { return Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(v.toString())); } catch (Exception e) { return null; }
    }
    private record BfsNode(String nodeId, String nodeType, int depth) {}
}