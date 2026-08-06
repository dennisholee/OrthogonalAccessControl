package com.oac.decision.application.port.shared;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Database-agnostic bounded-BFS relationship evaluator — the single source of truth for
 * {@code hasRelationship} traversal.
 * <p>
 * Both {@code MongoRelationshipGraphAdapter} and the emulator's in-memory adapter delegate
 * here so the emulator's ReBAC decisions mirror the core build exactly. The algorithm is a
 * faithful port of the MongoDB adapter's BFS: type is enforced only on the direct edge from
 * the original subject (depth 0); intermediate hops traverse any relationship type, and both
 * forward (subject → resource) and reverse (resource → subject) edges are explored.
 */
public final class RelationshipBfsEvaluator {

    private static final int DEFAULT_MAX_DEPTH = 3;

    private RelationshipBfsEvaluator() {
    }

    /**
     * @param edges            relationship edges with {@code subjectId}, {@code resourceId},
     *                         {@code relationshipType}, optional {@code expiresAt} and optional
     *                         {@code boundaryScope} (Map) keys
     * @param maxDepth         maximum traversal depth (<=0 uses the default)
     * @param boundaryScope    policy-declared relationship boundary scope; when non-null and
     *                         non-empty, only edges whose {@code boundaryScope} matches all
     *                         declared dimensions are traversed (Section 4.36)
     */
    public static boolean hasRelationship(
            List<Map<String, Object>> edges,
            String subjectId,
            String resourceId,
            String relationshipType,
            int maxDepth,
            Map<String, String> boundaryScope
    ) {
        if (maxDepth <= 0) maxDepth = DEFAULT_MAX_DEPTH;
        Set<String> visited = new HashSet<>();
        Deque<BfsNode> queue = new ArrayDeque<>();
        queue.add(new BfsNode(subjectId, "subject", 0));

        while (!queue.isEmpty()) {
            BfsNode current = queue.pollFirst();
            if (current.depth > maxDepth) continue;
            if (!visited.add(current.nodeType + ":" + current.nodeId)) continue;

            // Direct forward: subjectId=current → resourceId=target.
            // The type constraint is applied only at depth 0.
            String effectiveType = (current.depth == 0) ? relationshipType : null;
            if (hasDirectEdge(edges, current.nodeId, resourceId, effectiveType, boundaryScope)) {
                return true;
            }

            // Reverse: subjectId=target → resourceId=current (for depth > 0)
            if (current.depth > 0 && hasDirectEdge(edges, resourceId, current.nodeId, effectiveType, boundaryScope)) {
                return true;
            }

            if (current.depth >= maxDepth) continue;

            // Forward edges (subject → resource)
            for (Map<String, Object> edge : edges) {
                if (isExpired(edge)) continue;
                if (!boundaryScopeMatches(edge, boundaryScope)) continue;
                if (str(edge, "subjectId") != null && str(edge, "subjectId").equals(current.nodeId)) {
                    queue.add(new BfsNode(str(edge, "resourceId"), "resource", current.depth + 1));
                }
            }

            // Reverse edges (resource ← subject)
            for (Map<String, Object> edge : edges) {
                if (isExpired(edge)) continue;
                if (!boundaryScopeMatches(edge, boundaryScope)) continue;
                if (str(edge, "resourceId") != null && str(edge, "resourceId").equals(current.nodeId)) {
                    String otherType = "subject".equals(current.nodeType) ? "resource" : "subject";
                    String otherId = "subject".equals(current.nodeType)
                            ? str(edge, "resourceId") : str(edge, "subjectId");
                    queue.add(new BfsNode(otherId, otherType, current.depth + 1));
                }
            }
        }
        return false;
    }

    /**
     * Backward-compatible overload without boundary scope — treated as unscoped traversal.
     */
    public static boolean hasRelationship(
            List<Map<String, Object>> edges,
            String subjectId,
            String resourceId,
            String relationshipType,
            int maxDepth
    ) {
        return hasRelationship(edges, subjectId, resourceId, relationshipType, maxDepth, null);
    }

    private static boolean hasDirectEdge(
            List<Map<String, Object>> edges, String from, String to, String type,
            Map<String, String> boundaryScope) {
        for (Map<String, Object> edge : edges) {
            if (isExpired(edge)) continue;
            if (!boundaryScopeMatches(edge, boundaryScope)) continue;
            String s = str(edge, "subjectId");
            String r = str(edge, "resourceId");
            if (s == null || r == null) continue;
            if (s.equals(from) && r.equals(to)) {
                if (type == null) return true;
                String edgeType = str(edge, "relationshipType");
                if (edgeType != null && edgeType.equals(type)) return true;
            }
        }
        return false;
    }

    /**
     * Boundary-scope filter (Section 4.36): when the policy declares a {@code boundaryScope},
     * every edge traversed must declare a matching scope for all declared dimensions.
     * <ul>
     *   <li>Policy declares scope, edge has no scope → excluded.</li>
     *   <li>Policy declares {@code {market: retail, lob: cards}}, edge has only
     *       {@code {market: retail}} → excluded (partial match is not sufficient).</li>
     *   <li>Policy has no scope → all edges pass (unscoped traversal).</li>
     * </ul>
     */
    private static boolean boundaryScopeMatches(Map<String, Object> edge, Map<String, String> boundaryScope) {
        if (boundaryScope == null || boundaryScope.isEmpty()) return true;
        Object edgeScopeObj = edge.get("boundaryScope");
        if (!(edgeScopeObj instanceof Map<?, ?> edgeScope)) return false;
        for (Map.Entry<String, String> entry : boundaryScope.entrySet()) {
            Object edgeValue = edgeScope.get(entry.getKey());
            if (edgeValue == null) return false;
            if (!entry.getValue().equals(edgeValue.toString())) return false;
        }
        return true;
    }

    private static boolean isExpired(Map<String, Object> edge) {
        Object exp = edge.get("expiresAt");
        if (exp == null) return false;
        try {
            return Instant.now().isAfter(Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(exp.toString())));
        } catch (Exception e) {
            return false;
        }
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private record BfsNode(String nodeId, String nodeType, int depth) {
    }
}
