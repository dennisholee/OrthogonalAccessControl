package com.oac.decision.model;

/**
 * Base class for all policy-domain exceptions.
 * <p>
 * Carries a stable {@code decisionCode} (surfaced in the error envelope) and an HTTP
 * status so a single exception handler can map the whole hierarchy. The status is a
 * plain {@code int} to keep the domain model framework-free.
 */
public abstract class PolicyDomainException extends RuntimeException {

    private final String decisionCode;
    private final int httpStatus;

    protected PolicyDomainException(String decisionCode, String message, int httpStatus) {
        super(message);
        this.decisionCode = decisionCode;
        this.httpStatus = httpStatus;
    }

    public String decisionCode() {
        return decisionCode;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
