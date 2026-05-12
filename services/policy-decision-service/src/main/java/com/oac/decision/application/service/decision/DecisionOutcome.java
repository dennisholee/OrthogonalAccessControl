package com.oac.decision.application.service.decision;

public record DecisionOutcome(
        String decision,
        String decisionCode,
        String evidenceRef
) {
}