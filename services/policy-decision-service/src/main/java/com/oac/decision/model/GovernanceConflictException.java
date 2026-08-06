package com.oac.decision.model;

/**
 * Raised for policy lifecycle conflicts (maker-checker, invalid state transitions,
 * quorum failures). Maps to HTTP 409.
 */
public class GovernanceConflictException extends PolicyDomainException {

    public GovernanceConflictException(String decisionCode, String message) {
        super(decisionCode, message, 409);
    }
}
