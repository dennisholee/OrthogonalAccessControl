package com.oac.decision.model;

/**
 * Access level granted to a specific attribute or field of a resource.
 * Used for attribute-level authorization decisions on MongoDB documents.
 */
public enum AttributeAccessLevel {
    /** Full read access — field value is returned as-is */
    READ,
    /** Full write access — field may be created or modified */
    WRITE,
    /** Field value is masked/redacted — caller sees a sanitized value */
    MASK,
    /** Field is entirely hidden from the caller */
    HIDDEN,
    /** Explicitly denied — access forbidden even at field level */
    DENY
}