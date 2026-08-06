package com.oac.emulator;

import com.oac.decision.application.port.out.RelationshipGraphPort;
import com.oac.decision.application.port.shared.RelationshipBfsEvaluator;
import com.oac.decision.model.CheckPermissionRequest;
import com.oac.decision.model.RelationshipEdge;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory replacement for {@code MongoRelationshipGraphAdapter}.
 * Implements bounded BFS graph traversal over an adjacency list,
 * functionally identical to MongoDB's {@code $graphLookup}.
 *
 * <p>No middleware dependencies — pure Java data structures.</p>
 */
public class InMemoryRelationshipGraphAdapter implements RelationshipGraphPort {

    /** Map from nodeId → list of outbound edges (subjectId → resourceId). */
    private final Map<String, List<Edge>> adjacencyList = new LinkedHashMap<>();
    /** Map from nodeId → list of reverse edges (resourceId → subjectId). */
    private final Map<String, List<Edge>> reverseAdjacency = new LinkedHashMap<>();
    /** All stored relationship edges for reference. */
    private final Map<String, Edge> edgesById = new LinkedHashMap<>();

    private int maxDepth = 3;

    public InMemoryRelationshipGraphAdapter(List<Map<String, Object>> relationships) {
        if (relationships != null) {
            for (Map<String, Object> rel : relationships) {
                Object scope = rel.get("boundaryScope");
                @SuppressWarnings("unchecked")
                Map<String, Object> boundaryScope = scope instanceof Map<?, ?> m
                        ? (Map<String, Object>) m : null;
                var edge = new Edge(
                        nextId(),
                        str(rel, "subjectId"),
                        str(rel, "subjectType"),
                        str(rel, "relationshipType"),
                        str(rel, "resourceId"),
                        str(rel, "resourceType"),
                        str(rel, "expiresAt"),
                        boundaryScope
                );
                edgesById.put(edge.id, edge);
                adjacencyList.computeIfAbsent(edge.subjectId, k -> new ArrayList<>()).add(edge);
                reverseAdjacency.computeIfAbsent(edge.resourceId, k -> new ArrayList<>()).add(edge);
            }
        }
    }

    private static long idCounter = 0;
    private synchronized String nextId() { return "edge-" + (++idCounter); }

    @Override
    public List<RelationshipEdge> findRelationships(CheckPermissionRequest request) {
        return adjacencyList.getOrDefault(request.subject().id(), List.of()).stream()
                .filter(e -> e.resourceId.equals(request.resource().id()))
                .filter(e -> !isExpired(e.expiresAt))
                .map(e -> new RelationshipEdge(e.id, e.subjectId, e.subjectType,
                        e.relationshipType, e.resourceId, e.resourceType,
                        null, null, null, null))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> findRelatedResourceIds(String subjectId, String resourceType,
                                                String relationshipType, int maxDepth) {
        if (maxDepth <= 0) maxDepth = this.maxDepth;
        Set<String> visited = new HashSet<>();
        ArrayDeque<BfsNode> queue = new ArrayDeque<>();
        Set<String> result = new HashSet<>();
        queue.add(new BfsNode(subjectId, "subject", 0));
        while (!queue.isEmpty()) {
            BfsNode current = queue.pollFirst();
            if (current.depth > maxDepth) continue;
            if (!visited.add(current.type + ":" + current.id)) continue;
            if (current.depth > 0 && "resource".equals(current.type)) {
                result.add(current.id);
                continue;
            }
            List<Edge> edges = "subject".equals(current.type)
                    ? adjacencyList.getOrDefault(current.id, List.of())
                    : reverseAdjacency.getOrDefault(current.id, List.of());
            for (Edge edge : edges) {
                if (isExpired(edge.expiresAt)) continue;
                if (relationshipType != null && !relationshipType.equals(edge.relationshipType)) continue;
                String nextId = "subject".equals(current.type) ? edge.resourceId : edge.subjectId;
                String nextType = "subject".equals(current.type) ? "resource" : "subject";
                queue.add(new BfsNode(nextId, nextType, current.depth + 1));
            }
        }
        return new ArrayList<>(result);
    }

    @Override
    public Set<String> traverseResources(String subjectId, String resourceType, int maxDepth) {
        if (maxDepth <= 0) maxDepth = this.maxDepth;
        Set<String> visited = new HashSet<>();
        ArrayDeque<BfsNode> queue = new ArrayDeque<>();
        Set<String> result = new HashSet<>();
        queue.add(new BfsNode(subjectId, "subject", 0));
        while (!queue.isEmpty()) {
            BfsNode current = queue.pollFirst();
            if (current.depth > maxDepth) continue;
            if (!visited.add(current.type + ":" + current.id)) continue;
            if (current.depth > 0 && "resource".equals(current.type)) {
                result.add(current.id);
                if (current.depth < maxDepth)
                    queue.add(new BfsNode(current.id, "resource_continue", current.depth));
                continue;
            }
            List<Edge> edges = "subject".equals(current.type)
                    ? adjacencyList.getOrDefault(current.id, List.of())
                    : reverseAdjacency.getOrDefault(current.id, List.of());
            for (Edge edge : edges) {
                if (isExpired(edge.expiresAt)) continue;
                String nextId = "subject".equals(current.type) ? edge.resourceId : edge.subjectId;
                String nextType = "subject".equals(current.type) ? "resource" : "subject";
                queue.add(new BfsNode(nextId, nextType, current.depth + 1));
            }
        }
        return result;
    }

    @Override
    public boolean hasRelationship(String subjectId, String resourceId, String relationshipType, int maxDepth) {
        if (maxDepth <= 0) maxDepth = this.maxDepth;
        // Delegate to the shared RelationshipBfsEvaluator — the same single source of truth
        // used by MongoRelationshipGraphAdapter, guaranteeing identical traversal.
        return RelationshipBfsEvaluator.hasRelationship(allEdgeMaps(), subjectId, resourceId, relationshipType, maxDepth);
    }

    @Override
    public boolean hasRelationship(String subjectId, String resourceId, String relationshipType, int maxDepth,
                                   Map<String, String> boundaryScope) {
        if (maxDepth <= 0) maxDepth = this.maxDepth;
        return RelationshipBfsEvaluator.hasRelationship(
                allEdgeMaps(), subjectId, resourceId, relationshipType, maxDepth, boundaryScope);
    }

    /** All stored edges as Map documents, the shared evaluator's input contract. */
    private List<Map<String, Object>> allEdgeMaps() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Edge edge : edgesById.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("subjectId", edge.subjectId);
            map.put("subjectType", edge.subjectType);
            map.put("relationshipType", edge.relationshipType);
            map.put("resourceId", edge.resourceId);
            map.put("resourceType", edge.resourceType);
            map.put("expiresAt", edge.expiresAt);
            if (edge.boundaryScope != null && !edge.boundaryScope.isEmpty()) {
                map.put("boundaryScope", edge.boundaryScope);
            }
            result.add(map);
        }
        return result;
    }

    @Override
    public String createRelationship(RelationshipEdge edge) {
        var stored = new Edge(nextId(), edge.subjectId(), edge.subjectType(),
                edge.relationshipType(), edge.resourceId(), edge.resourceType(), null, null);
        edgesById.put(stored.id, stored);
        adjacencyList.computeIfAbsent(stored.subjectId, k -> new ArrayList<>()).add(stored);
        reverseAdjacency.computeIfAbsent(stored.resourceId, k -> new ArrayList<>()).add(stored);
        return stored.id;
    }

    @Override
    public void revokeRelationship(String relationshipId) {
        Edge removed = edgesById.remove(relationshipId);
        if (removed != null) {
            adjacencyList.getOrDefault(removed.subjectId, List.of()).remove(removed);
            reverseAdjacency.getOrDefault(removed.resourceId, List.of()).remove(removed);
        }
    }

    @Override
    public int getMaxTraversalDepth() { return maxDepth; }

    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }

    private boolean isExpired(String expiresAt) {
        if (expiresAt == null) return false;
        try { return Instant.now().isAfter(Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(expiresAt))); }
        catch (Exception e) { return false; }
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    /** Internal edge data class. */
    private static class Edge {
        final String id, subjectId, subjectType, relationshipType, resourceId, resourceType, expiresAt;
        final Map<String, Object> boundaryScope;
        Edge(String id, String subjectId, String subjectType, String relationshipType,
             String resourceId, String resourceType, String expiresAt,
             Map<String, Object> boundaryScope) {
            this.id = id; this.subjectId = subjectId; this.subjectType = subjectType;
            this.relationshipType = relationshipType; this.resourceId = resourceId;
            this.resourceType = resourceType; this.expiresAt = expiresAt;
            this.boundaryScope = boundaryScope;
        }
    }

    private record BfsNode(String id, String type, int depth) {}
}