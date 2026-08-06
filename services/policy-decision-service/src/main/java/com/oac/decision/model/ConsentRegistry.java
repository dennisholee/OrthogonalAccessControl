package com.oac.decision.model;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-subject consent registry document (docs/POLICY_ARCHITECTURE.md Section 4.19).
 * <p>
 * Each subject has one ConsentRegistry containing per-purpose consent attributes.
 * The registry is versioned — every mutation increments {@code consentVersion}.
 */
public class ConsentRegistry {

    private String subjectId;
    private long consentVersion;
    private Map<String, Object> consentAttributes = new LinkedHashMap<>();
    private OffsetDateTime lastUpdated;

    public ConsentRegistry() {
    }

    public ConsentRegistry(String subjectId, long consentVersion, Map<String, Object> consentAttributes) {
        this.subjectId = subjectId;
        this.consentVersion = consentVersion;
        this.consentAttributes = consentAttributes == null ? new LinkedHashMap<>() : consentAttributes;
        this.lastUpdated = OffsetDateTime.now();
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(String subjectId) {
        this.subjectId = subjectId;
    }

    public long getConsentVersion() {
        return consentVersion;
    }

    public void setConsentVersion(long consentVersion) {
        this.consentVersion = consentVersion;
    }

    public Map<String, Object> getConsentAttributes() {
        return consentAttributes;
    }

    public void setConsentAttributes(Map<String, Object> consentAttributes) {
        this.consentAttributes = consentAttributes;
    }

    public OffsetDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(OffsetDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    /**
     * Returns the consent status for a specific consent attribute, or null if absent.
     * The attribute value may be a simple String or a Map with a "status" key.
     */
    public String consentStatus(String attributeName) {
        Object value = consentAttributes.get(attributeName);
        if (value == null) return null;
        if (value instanceof String s) return s.toUpperCase();
        if (value instanceof Map<?, ?> m) {
            Object status = m.get("status");
            return status == null ? null : status.toString().toUpperCase();
        }
        return null;
    }

    /** Convenience factory for a granted consent state. */
    public static ConsentRegistry granted(String subjectId, long version, String... grantedAttributes) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        for (String attr : grantedAttributes) {
            attrs.put(attr, "GRANTED");
        }
        return new ConsentRegistry(subjectId, version, attrs);
    }

    /** Convenience factory for a withdrawn consent state. */
    public static ConsentRegistry withdrawn(String subjectId, long version, String... withdrawnAttributes) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        for (String attr : withdrawnAttributes) {
            attrs.put(attr, "WITHDRAWN");
        }
        return new ConsentRegistry(subjectId, version, attrs);
    }
}