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
        OffsetDateTime evaluatedAt
) {
}
