package com.oac.decision.application.service.decision.rules.caveats;

import com.oac.decision.application.service.decision.CaveatEvaluator;
import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.model.AttributeAccessMap;

import java.util.Map;
import java.util.Optional;

/**
 * Aggregation required caveat (Section 4.25).
 * <p>
 * When the request declares {@code minGroupSize}, the PDP denies with
 * {@code DECISION_AGGREGATION_REQUIRED} when the declared {@code resultSize}
 * (the PEP's row count for the response) is below the threshold — the PEP MUST then
 * suppress individual rows and return only the aggregated summary. A request without
 * {@code minGroupSize} passes (the caveat is not active). {@code suppressIndividualRows}
 * is an obligation consumed by the PEP; it does not fail the decision by itself.
 */
public class AggregationRequiredCaveatEvaluator implements CaveatEvaluator {

    @Override
    public boolean evaluate(DecisionContext context, Map<String, Object> caveatParams) {
        return failureCode(context).isEmpty();
    }

    @Override
    public Optional<String> failureDecisionCode(DecisionContext context, Map<String, Object> caveatParams) {
        return failureCode(context);
    }

    private Optional<String> failureCode(DecisionContext context) {
        Long minGroupSize = number(lookup(context, "minGroupSize"));
        if (minGroupSize == null) {
            return Optional.empty(); // aggregation not required
        }
        Long resultSize = number(lookup(context, "resultSize"));
        if (resultSize != null && resultSize < minGroupSize) {
            return Optional.of("DECISION_AGGREGATION_REQUIRED");
        }
        return Optional.empty();
    }

    private Object lookup(DecisionContext context, String key) {
        Object value = context.resolvedRuntimeContext().get(key);
        if (value != null) {
            return value;
        }
        return context.request().runtimeContext() == null
                ? null : context.request().runtimeContext().get(key);
    }

    private Long number(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
