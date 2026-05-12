package com.oac.decision.model;

public class GovernanceConflictException extends RuntimeException {

    private final String decisionCode;

    public GovernanceConflictException(String decisionCode, String message) {
        super(message);
        this.decisionCode = decisionCode;
    }

    public String decisionCode() {
        return decisionCode;
    }
}