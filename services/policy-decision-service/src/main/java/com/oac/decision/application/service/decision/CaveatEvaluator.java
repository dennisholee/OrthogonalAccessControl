package com.oac.decision.application.service.decision;

import com.oac.decision.model.AttributeAccessLevel;
import com.oac.decision.model.AttributeAccessMap;

import java.util.Map;

/**
 * Evaluates caveats attached to ALLOW policies. Caveats can:
 * 1. Narrow a decision (ALLOW → DENY) if conditions are not met.
 * 2. Enrich obligations with field-level masking instructions.
 */
public interface CaveatEvaluator {

    /**
     * Evaluate whether the caveat conditions are satisfied.
     *
     * @param context      the full decision context
     * @param caveatParams parameters specific to this caveat instance
     * @return true if the caveat passes, false if it should narrow to DENY
     */
    boolean evaluate(DecisionContext context, Map<String, Object> caveatParams);

    /**
     * Compute attribute-level access constraints based on this caveat.
     * For FIELD_MASK caveats, this returns which fields are restricted.
     *
     * @param requestedFields set of field names the request is asking for
     * @param caveatParams    parameters specific to this caveat instance
     * @return AttributeAccessMap with field-level constraints
     */
    default AttributeAccessMap applyFieldConstraints(
            java.util.Set<String> requestedFields,
            Map<String, Object> caveatParams
    ) {
        return AttributeAccessMap.empty();
    }
}