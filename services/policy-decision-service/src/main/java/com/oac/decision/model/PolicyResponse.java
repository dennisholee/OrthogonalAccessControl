package com.oac.decision.model;

public record PolicyResponse(
        String policyId,
        int version,
        PolicyState state,
        PolicyRiskLevel riskLevel,
        String decisionCode,
        String evidenceRef
) {
}