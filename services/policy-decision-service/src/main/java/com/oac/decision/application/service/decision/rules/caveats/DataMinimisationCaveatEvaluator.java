package com.oac.decision.application.service.decision.rules.caveats;

import com.oac.decision.application.service.decision.CaveatEvaluator;
import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.model.AttributeAccessLevel;
import com.oac.decision.model.AttributeAccessMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * DataMinimisationCaveatEvaluator — implements the DataMinimisation obligation
 * (docs/POLICY_ARCHITECTURE.md Section 4.18).
 * <p>
 * Purpose-aware field selection: the PEP selects the field access map based on the
 * purpose declared in the decision request. Fields not listed in the map for a given
 * purpose default to NONE.
 * <p>
 * Masking levels supported: READ, NONE, MASK, HASH_SHA256, PSEUDONYMISE,
 * AGGREGATE_YEAR_ONLY, AGGREGATE_MONTH_ONLY.
 */
public class DataMinimisationCaveatEvaluator implements CaveatEvaluator {

    @Override
    public boolean evaluate(DecisionContext context, Map<String, Object> caveatParams) {
        // Only enforce data minimisation when a purposeFieldMaps is configured.
        // If no purposeFieldMaps is present, this evaluator does not narrow the decision.
        Object purposeMapsObj = caveatParams.get("purposeFieldMaps");
        if (!(purposeMapsObj instanceof Map<?, ?> maps) || maps.isEmpty()) {
            return true;
        }

        // Determine the purpose from request boundary context
        var bc = context.request().boundaryContext();
        String purpose = bc != null ? bc.purpose() : null;
        if (purpose == null || purpose.isBlank()) {
            // No declared purpose — data minimisation cannot be applied.
            return true;
        }

        // If the purpose is not listed in the maps, it's a violation
        return maps.containsKey(purpose);
    }

    @Override
    public AttributeAccessMap applyFieldConstraints(
            Set<String> requestedFields,
            Map<String, Object> caveatParams
    ) {
        Map<String, AttributeAccessLevel> fieldAccess = new HashMap<>();

        Object purposeMapsObj = caveatParams.get("purposeFieldMaps");
        if (!(purposeMapsObj instanceof Map<?, ?> purposeMaps)) {
            return AttributeAccessMap.empty();
        }

        // The AllowRule injects the resolved purpose into caveatParams under "_purpose"
        String purpose = caveatParams.get("_purpose") instanceof String p ? p : null;
        if (purpose == null) {
            return AttributeAccessMap.empty();
        }

        Object purposeMapObj = purposeMaps.get(purpose);
        if (!(purposeMapObj instanceof Map<?, ?> purposeMap)) {
            return AttributeAccessMap.empty();
        }

        for (var entry : purposeMap.entrySet()) {
            String field = entry.getKey().toString();
            String levelStr = entry.getValue().toString().toUpperCase();
            AttributeAccessLevel level = mapToAttributeAccessLevel(levelStr);
            if (level != null) {
                fieldAccess.put(field, level);
            }
        }

        return new AttributeAccessMap(fieldAccess, Map.of());
    }

    private AttributeAccessLevel mapToAttributeAccessLevel(String maskingLevel) {
        return switch (maskingLevel) {
            case "READ" -> AttributeAccessLevel.READ;
            case "NONE" -> AttributeAccessLevel.HIDDEN;
            case "MASK", "HASH_SHA256", "PSEUDONYMISE",
                 "AGGREGATE_YEAR_ONLY", "AGGREGATE_MONTH_ONLY" -> AttributeAccessLevel.MASK;
            default -> null;
        };
    }
}