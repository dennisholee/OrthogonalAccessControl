package com.oac.decision.model;

public record PolicyResponse(
        String policyId,
        int version,
        PolicyState state,
        PolicyRiskLevel riskLevel,
        String decisionCode,
        String evidenceRef,
        String consistencyToken
) {
    public PolicyResponse(String policyId, int version, PolicyState state, PolicyRiskLevel riskLevel,
                          String decisionCode, String evidenceRef) {
        this(policyId, version, state, riskLevel, decisionCode, evidenceRef, null);
    }
}
