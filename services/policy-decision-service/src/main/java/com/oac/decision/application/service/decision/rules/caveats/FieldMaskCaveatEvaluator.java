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

        // Field-level entries (specific field -> access level)
        Object fieldLevels = caveatParams.get("fields");
        if (fieldLevels instanceof Map<?, ?> fieldMap) {
            for (var entry : fieldMap.entrySet()) {
                String fieldName = entry.getKey().toString();
                String levelStr = entry.getValue().toString().toUpperCase();
                try {
                    fieldAccess.put(fieldName, AttributeAccessLevel.valueOf(levelStr));
                } catch (IllegalArgumentException ignored) {
                    // Skip unrecognized levels
                }
            }
        }

        // Tag-based entries (tag pattern -> access level for bulk classification)
        Object tagLevels = caveatParams.get("tags");
        if (tagLevels instanceof Map<?, ?> tagMap) {
            for (var entry : tagMap.entrySet()) {
                String tag = entry.getKey().toString();
                String levelStr = entry.getValue().toString().toUpperCase();
                try {
                    tagAccess.put(tag, AttributeAccessLevel.valueOf(levelStr));
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
}