package com.oac.decision.model;

/**
 * Raised when a principal is not authorized to perform a policy lifecycle
 * operation (separation of duties). Maps to HTTP 403.
 */
public class PolicyAuthorizationException extends PolicyDomainException {

    public PolicyAuthorizationException(String decisionCode, String message) {
        super(decisionCode, message, 403);
    }
}

