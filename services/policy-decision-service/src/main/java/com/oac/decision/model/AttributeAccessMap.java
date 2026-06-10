package com.oac.decision.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Hybrid attribute-level access map combining field-level ACL entries with tag-based
 * sensitivity classification. Supports MongoDB query filter generation for field-level
 * projection, masking, and redaction.
 */
public record AttributeAccessMap(
        Map<String, AttributeAccessLevel> fieldAccess,
        Map<String, AttributeAccessLevel> tagAccess
) {
    public static AttributeAccessMap empty() {
        return new AttributeAccessMap(Map.of(), Map.of());
    }

    public AttributeAccessMap {
        fieldAccess = fieldAccess == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(fieldAccess));
        tagAccess = tagAccess == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(tagAccess));
    }

    public AttributeAccessLevel forField(String fieldName) {
        AttributeAccessLevel fieldLevel = fieldAccess.get(fieldName);
        if (fieldLevel != null) {
            return fieldLevel;
        }
        for (var entry : tagAccess.entrySet()) {
            if (fieldName.startsWith(entry.getKey()) || fieldName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return AttributeAccessLevel.READ;
    }

    public Set<String> getMaskedFields() {
        return getFieldsAtLevel(AttributeAccessLevel.MASK);
    }

    public Set<String> getHiddenFields() {
        return getFieldsAtLevel(AttributeAccessLevel.HIDDEN);
    }

    public Set<String> getDeniedFields() {
        return getFieldsAtLevel(AttributeAccessLevel.DENY);
    }

    private Set<String> getFieldsAtLevel(AttributeAccessLevel level) {
        return fieldAccess.entrySet().stream()
                .filter(e -> e.getValue() == level)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}