package com.oac.decision.application.service.decision.rules.caveats;

import com.oac.decision.application.service.decision.CaveatEvaluator;
import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.model.AttributeAccessMap;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Export destination control and cross-border transfer validation (Section 4.20).
 * <p>
 * When the request declares {@code destinationConstraints} (a map of authorised
 * destinations to their {@code transferMechanism} / field constraints), the evaluator:
 * <ul>
 *   <li>denies with {@code DECISION_EXPORT_RESTRICTED} when the declared
 *       {@code exportDestination} is not in the constraint map (or is absent);</li>
 *   <li>denies with {@code DECISION_CROSS_BORDER_TRANSFER_BLOCKED} when the resource
 *       {@code regulatoryRegime} is {@code GDPR} and the destination has no
 *       {@code transferMechanism} or its {@code validUntil} has expired.</li>
 * </ul>
 * A request without {@code destinationConstraints} passes (the caveat is not active).
 */
public class ExportRestrictionCaveatEvaluator implements CaveatEvaluator {

    @Override
    public boolean evaluate(DecisionContext context, Map<String, Object> caveatParams) {
        return failureCode(context).isEmpty();
    }

    @Override
    public Optional<String> failureDecisionCode(DecisionContext context, Map<String, Object> caveatParams) {
        return failureCode(context);
    }

    @SuppressWarnings("unchecked")
    private Optional<String> failureCode(DecisionContext context) {
        Object constraintsObj = lookup(context, "destinationConstraints");
        if (!(constraintsObj instanceof Map<?, ?> constraints) || constraints.isEmpty()) {
            return Optional.empty(); // no export restriction declared
        }
        String destination = str(lookup(context, "exportDestination"));
        if (destination == null) {
            return Optional.of("DECISION_EXPORT_RESTRICTED");
        }
        Object destDefObj = constraints.get(destination);
        if (!(destDefObj instanceof Map<?, ?> destDef)) {
            return Optional.of("DECISION_EXPORT_RESTRICTED");
        }

        // Cross-border transfer validation: GDPR resource requires a valid mechanism
        String regime = str(lookup(context, "resourceRegulatoryRegime"));
        if ("GDPR".equals(regime)) {
            Object tmObj = destDef.get("transferMechanism");
            if (!(tmObj instanceof Map<?, ?> tm)) {
                return Optional.of("DECISION_CROSS_BORDER_TRANSFER_BLOCKED");
            }
            Object validUntil = tm.get("validUntil");
            if (validUntil != null && isExpired(validUntil)) {
                return Optional.of("DECISION_CROSS_BORDER_TRANSFER_BLOCKED");
            }
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

    private String str(Object value) {
        return value == null ? null : value.toString();
    }

    private boolean isExpired(Object validUntil) {
        try {
            Instant expiry = Instant.parse(validUntil.toString());
            return expiry.isBefore(Instant.now());
        } catch (Exception e) {
            return false; // unparseable validity — treat as valid (fail-open on data)
        }
    }
}
