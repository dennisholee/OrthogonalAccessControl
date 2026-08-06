package com.oac.decision.model;

/**
 * Raised when a policy spec fails structural validation at creation time.
 * Maps to HTTP 400 with {@code VALIDATION_ERROR}.
 */
public class PolicyValidationException extends PolicyDomainException {

    public PolicyValidationException(String message) {
        super("VALIDATION_ERROR", message, 400);
    }
}

