package com.oac.enforcement;

import java.util.Map;

public interface DecisionClient {

    boolean checkPermission(String subjectId, String action, String resourceId);

    /**
     * Extended check that returns enriched decision info including field masks.
     * Default implementation returns null (no masks).
     */
    default Map<String, Object> checkPermissionWithDetails(
            String subjectId, String action, String resourceId,
            Map<String, Object> boundaryOverride) {
        boolean allowed = checkPermission(subjectId, action, resourceId);
        return Map.of(
                "decision", allowed ? "ALLOW" : "DENY",
                "decisionCode", allowed ? "DECISION_POLICY_ALLOW" : "DECISION_DEFAULT_DENY"
        );
    }
}