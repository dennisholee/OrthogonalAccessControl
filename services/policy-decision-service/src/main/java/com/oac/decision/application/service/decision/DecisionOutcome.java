package com.oac.decision.application.service.decision;

import com.oac.decision.model.AttributeAccessMap;

public record DecisionOutcome(
        String decision,
        String decisionCode,
        String evidenceRef,
        AttributeAccessMap attributeAccessMap
) {
    public DecisionOutcome(String decision, String decisionCode, String evidenceRef) {
        this(decision, decisionCode, evidenceRef, AttributeAccessMap.empty());
    }
}
