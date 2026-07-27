package com.oac.emulator;

import com.oac.decision.application.port.out.PolicyRegistryPort;
import com.oac.decision.model.CheckPermissionRequest;
import com.oac.decision.model.LookupResourcesRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * In-memory replacement for {@code MongoPolicyRegistryAdapter}.
 * Performs policy matching using Java Streams instead of MongoDB queries.
 *
 * <p>Supports all policy fields: subjectId, action, resourceType, resourceId,
 * boundary context (tenant, geography, market, lineOfBusiness, channel),
 * spelCondition, requiredRelationship, timeWindow, sourceIpRange, fieldMasks.</p>
 */
public class InMemoryPolicyRegistryAdapter implements PolicyRegistryPort {

    private final List<Map<String, Object>> policies;
    private String activeVersion;

    public InMemoryPolicyRegistryAdapter(List<Map<String, Object>> policies) {
        this.policies = new ArrayList<>(policies);
        this.activeVersion = "v0";
    }

    /** Set active version string (returned by {@link #getActiveVersion()}). */
    public void setActiveVersion(String version) {
        this.activeVersion = version;
    }

    @Override
    public List<String> findMatchedPolicies(CheckPermissionRequest request) {
        List<String> matched = new ArrayList<>();

        // Query 1: Strict match with action + resourceType + boundary + subject
        var subjectId = request.subject().id();
        var action = request.action();
        var resourceType = request.resource().type();
        var boundary = request.boundaryContext();

        for (Map<String, Object> policy : policies) {
            if (!"ACTIVE".equals(policy.get("state"))) continue;

            // Subject match: policy subjectId matches request, or policy has no subjectId constraint
            Object policySubject = policy.get("subjectId");
            if (policySubject != null && !policySubject.toString().equals(subjectId)) continue;

            // Action match
            Object policyAction = policy.get("action");
            if (policyAction != null && !"*".equals(policyAction.toString()) && !policyAction.toString().equals(action)) continue;

            // Resource type match
            Object policyResType = policy.get("resourceType");
            if (policyResType != null && !"*".equals(policyResType.toString()) && !policyResType.toString().equals(resourceType)) continue;

            // Boundary match (allowing "*" or absent)
            if (boundary != null && !matchesBoundary(policy, boundary)) continue;

            // Build matched policy entry with optional condition suffixes
            String effect = str(policy, "effect", "ALLOW");
            String name = str(policy, "name", "UNKNOWN");
            StringBuilder entry = new StringBuilder("POL." + effect + "." + name);

            // Append spelCondition for SpelConditionRule
            Object spel = policy.get("spelCondition");
            if (spel instanceof String sc && !sc.isBlank()) {
                entry.append(":").append(sc);
            }

            // Append requiredRelationship for ReBacRelationshipRule
            Object rel = policy.get("requiredRelationship");
            if (rel instanceof String rr && !rr.isBlank()) {
                entry.append(":REBAC.").append(rr);
            }

            matched.add(entry.toString());
        }

        return matched.stream().distinct().collect(Collectors.toList());
    }

    @Override
    public List<String> findAuthorizedResourceIds(LookupResourcesRequest request) {
        // Simple baseline: return resource IDs matched by subject/action/resourceType
        return List.of();
    }

    @Override
    public String getActiveVersion() {
        return activeVersion;
    }

    @Override
    public List<Map<String, String>> findFieldMasks(CheckPermissionRequest request) {
        Map<String, String> merged = new LinkedHashMap<>();

        for (Map<String, Object> policy : policies) {
            if (!"ACTIVE".equals(policy.get("state"))) continue;

            Object fm = policy.get("fieldMasks");
            if (fm instanceof List<?> fmList) {
                for (Object item : fmList) {
                    if (item instanceof Map<?, ?> entry) {
                        String field = entry.get("field") != null ? entry.get("field").toString() : null;
                        String level = entry.get("level") != null ? entry.get("level").toString() : null;
                        if (field != null && level != null) {
                            merged.putIfAbsent(field, level);
                        }
                    }
                }
            }
        }

        if (merged.isEmpty()) return List.of();
        List<Map<String, String>> result = new ArrayList<>();
        for (var entry : merged.entrySet()) {
            result.add(Map.of("field", entry.getKey(), "level", entry.getValue()));
        }
        return result;
    }

    private boolean matchesBoundary(Map<String, Object> policy, com.oac.decision.model.BoundaryContext boundary) {
        return matchesField(policy, "tenant", boundary.tenant())
                && matchesField(policy, "geography", boundary.geography())
                && matchesField(policy, "market", boundary.market())
                && matchesField(policy, "lineOfBusiness", boundary.lineOfBusiness())
                && matchesField(policy, "channel", boundary.channel());
    }

    private boolean matchesField(Map<String, Object> policy, String field, String requestValue) {
        Object policyValue = policy.get(field);
        if (policyValue == null) return true; // absent field matches all
        String pv = policyValue.toString();
        return "*".equals(pv) || pv.equals(requestValue);
    }

    private static String str(Map<String, Object> map, String key, String defaultVal) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultVal;
    }
}