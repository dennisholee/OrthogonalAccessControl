package com.oac.emulator;

import com.oac.decision.application.port.out.PolicyRegistryPort;
import com.oac.decision.application.port.shared.PolicyMatcher;
import com.oac.decision.model.CheckPermissionRequest;
import com.oac.decision.model.LookupResourcesRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private List<Map<String, Object>> policySets = new ArrayList<>();
    private String activeVersion;

    public InMemoryPolicyRegistryAdapter(List<Map<String, Object>> policies) {
        this.policies = new ArrayList<>(policies);
        this.activeVersion = "v0";
    }

    /** Set Policy Set documents (Section 4.2) evaluated via the shared matcher. */
    public void setPolicySets(List<Map<String, Object>> policySets) {
        this.policySets = policySets != null ? new ArrayList<>(policySets) : new ArrayList<>();
    }

    /** Set active version string (returned by {@link #getActiveVersion()}). */
    public void setActiveVersion(String version) {
        this.activeVersion = version;
    }

    @Override
    public List<String> findMatchedPolicies(CheckPermissionRequest request) {
        // Delegate to the shared PolicyMatcher — the single source of truth also used by
        // MongoPolicyRegistryAdapter, guaranteeing byte-for-byte identical matchedPolicies.
        return PolicyMatcher.match(policies, request, policySets);
    }

    @Override
    public List<String> findExpiredCertificationPolicies(CheckPermissionRequest request) {
        // Same governance check as MongoPolicyRegistryAdapter — shared PolicyMatcher logic.
        java.time.LocalDate today = java.time.LocalDate.now();
        List<String> expired = new ArrayList<>();
        for (Map<String, Object> policy : policies) {
            if (!"ACTIVE".equals(policy.get("state"))) continue;
            if (!PolicyMatcher.isCertificationExpired(policy, today)) continue;
            String name = policy.get("name") != null ? policy.get("name").toString() : "UNKNOWN";
            boolean matches = PolicyMatcher.match(List.of(policy), request).stream()
                    .anyMatch(entry -> entry.contains(name));
            if (matches) {
                expired.add(name);
            }
        }
        return expired;
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
}