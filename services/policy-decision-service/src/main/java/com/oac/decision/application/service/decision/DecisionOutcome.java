package com.oac.decision.application.service.decision;

import com.oac.decision.model.AttributeAccessMap;

import java.util.Map;

public record DecisionOutcome(
        String decision,
        String decisionCode,
        String evidenceRef,
        AttributeAccessMap attributeAccessMap,
        Map<String, Object> diagnostics
) {
    public DecisionOutcome(String decision, String decisionCode, String evidenceRef) {
        this(decision, decisionCode, evidenceRef, AttributeAccessMap.empty(), null);
    }

    public DecisionOutcome(String decision, String decisionCode, String evidenceRef,
                           AttributeAccessMap attributeAccessMap) {
        this(decision, decisionCode, evidenceRef, attributeAccessMap, null);
    }
}