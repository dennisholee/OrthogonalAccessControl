package com.oac.decision.application.service.decision.rules.caveats;

import com.oac.decision.application.service.decision.CaveatEvaluator;
import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.model.AttributeAccessLevel;
import com.oac.decision.model.AttributeAccessMap;

import java.util.*;

/**
 * Evaluates FIELD_MASK caveats. Determines which resource fields must be masked
 * (redacted), hidden, or denied when a policy applies.
 * Supports both field-level ACL entries and tag-based sensitivity classification.
 */
public class FieldMaskCaveatEvaluator implements CaveatEvaluator {

    @Override
    public boolean evaluate(DecisionContext context, Map<String, Object> caveatParams) {
        // FIELD_MASK caveats always evaluate to true — they enrich obligations
        // rather than narrow the decision
        return true;
    }

    @Override
    public AttributeAccessMap applyFieldConstraints(
            Set<String> requestedFields,
            Map<String, Object> caveatParams
    ) {
        Map<String, AttributeAccessLevel> fieldAccess = new HashMap<>();
        Map<String, AttributeAccessLevel> tagAccess = new HashMap<>();

        // Tag-based entries (tag pattern -> access level for bulk classification)
        // Process tags FIRST so we can detect when a field-level READ overrides a tag
        Object tagLevels = caveatParams.get("tags");
        if (tagLevels instanceof Map<?, ?> tagMap) {
            for (var entry : tagMap.entrySet()) {
                String tag = entry.getKey().toString();
                String levelStr = entry.getValue().toString().toUpperCase();
                try {
                    AttributeAccessLevel level = AttributeAccessLevel.valueOf(levelStr);
                    // Only include non-READ tags — READ means unrestricted
                    if (level != AttributeAccessLevel.READ) {
                        tagAccess.put(tag, level);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Skip unrecognized levels
                }
            }
        }

        // Field-level entries (specific field -> access level)
        Object fieldLevels = caveatParams.get("fields");
        if (fieldLevels instanceof Map<?, ?> fieldMap) {
            for (var entry : fieldMap.entrySet()) {
                String fieldName = entry.getKey().toString();
                String levelStr = entry.getValue().toString().toUpperCase();
                try {
                    AttributeAccessLevel level = AttributeAccessLevel.valueOf(levelStr);
                    // Only include non-READ fields — READ means unrestricted (no constraint)
                    // EXCEPT when the field name could be covered by a tag pattern —
                    // then READ signals an override of broader tag-based classification
                    if (level != AttributeAccessLevel.READ) {
                        fieldAccess.put(fieldName, level);
                    } else if (fieldOverridesTag(fieldName, tagAccess)) {
                        fieldAccess.put(fieldName, level);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Skip unrecognized levels
                }
            }
        }

        // Handle explicit mask list (list of field names to mask)
        Object maskFields = caveatParams.get("mask");
        if (maskFields instanceof List<?> maskList) {
            for (Object field : maskList) {
                fieldAccess.putIfAbsent(field.toString(), AttributeAccessLevel.MASK);
            }
        }

        // Handle explicit hidden list (list of field names to hide)
        Object hiddenFields = caveatParams.get("hidden");
        if (hiddenFields instanceof List<?> hiddenList) {
            for (Object field : hiddenList) {
                fieldAccess.putIfAbsent(field.toString(), AttributeAccessLevel.HIDDEN);
            }
        }

        return new AttributeAccessMap(fieldAccess, tagAccess);
    }

    /**
     * Checks whether a field-level READ entry should be preserved because it
     * overrides a broader tag-based classification. Returns true if any tag
     * key could match the given field name.
     */
    private boolean fieldOverridesTag(String fieldName, Map<String, AttributeAccessLevel> tagAccess) {
        if (tagAccess.isEmpty()) return false;
        for (String tagKey : tagAccess.keySet()) {
            // Tag patterns use wildcards like "*.email" — check if the field matches
            String pattern = tagKey.replace("*", "");
            if (fieldName.contains(pattern) || fieldName.equals(pattern)) {
                return true;
            }
        }
        return false;
    }
}