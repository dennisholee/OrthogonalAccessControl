package com.oac.decision.application.service.decision;

import com.oac.decision.model.CheckPermissionRequest;

import java.util.List;
import java.util.Map;

public record DecisionContext(
        CheckPermissionRequest request,
        List<String> matchedPolicies,
        Map<String, Object> resolvedRuntimeContext,
        boolean hasAllow
) {
}