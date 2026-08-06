package com.oac.decision.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record CheckPermissionResponse(
        String decision,
        String decisionCode,
        List<String> matchedPolicies,
        List<Map<String, Object>> obligations,
        List<String> explanationRefs,
        OffsetDateTime evaluatedAt,
        AttributeAccessMap attributeAccessMap,
        String explanation,
        String circuitBreakerState,
        String cacheStatus,
        Map<String, String> consistencyTokens
) {
    public CheckPermissionResponse(
            String decision,
            String decisionCode,
            List<String> matchedPolicies,
            List<Map<String, Object>> obligations,
            List<String> explanationRefs,
            OffsetDateTime evaluatedAt,
            AttributeAccessMap attributeAccessMap,
            String explanation
    ) {
        this(decision, decisionCode, matchedPolicies, obligations, explanationRefs, evaluatedAt,
                attributeAccessMap, explanation, null, null, Map.of());
    }

    public CheckPermissionResponse(
            String decision,
            String decisionCode,
            List<String> matchedPolicies,
            List<Map<String, Object>> obligations,
            List<String> explanationRefs,
            OffsetDateTime evaluatedAt,
            AttributeAccessMap attributeAccessMap
    ) {
        this(decision, decisionCode, matchedPolicies, obligations, explanationRefs, evaluatedAt,
                attributeAccessMap, null, null, null, Map.of());
    }

    public CheckPermissionResponse(
            String decision,
            String decisionCode,
            List<String> matchedPolicies,
            List<Map<String, Object>> obligations,
            List<String> explanationRefs,
            OffsetDateTime evaluatedAt
    ) {
        this(decision, decisionCode, matchedPolicies, obligations, explanationRefs, evaluatedAt,
                AttributeAccessMap.empty(), null, null, null, Map.of());
    }
}
