package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.service.decision.CaveatEvaluator;
import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;
import com.oac.decision.application.service.decision.rules.caveats.FieldMaskCaveatEvaluator;
import com.oac.decision.application.service.decision.rules.caveats.SourceIpRangeCaveatEvaluator;
import com.oac.decision.application.service.decision.rules.caveats.TimeWindowCaveatEvaluator;
import com.oac.decision.model.AttributeAccessLevel;
import com.oac.decision.model.AttributeAccessMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decision rule that evaluates explicit ALLOW policies with caveat support.
 * After detecting an ALLOW policy match, this rule evaluates all registered
 * caveat evaluators against the policy's caveat definitions (from runtime context).
 * Caveat failures can narrow the decision (ALLOW → DENY) or enrich it with
 * field-level access constraints.
 */
public class AllowRule implements DecisionRule {

    private final List<CaveatEvaluator> caveatEvaluators;

    public AllowRule() {
        this(List.of(
                new TimeWindowCaveatEvaluator(),
                new SourceIpRangeCaveatEvaluator(),
                new FieldMaskCaveatEvaluator()
        ));
    }

    public AllowRule(List<CaveatEvaluator> caveatEvaluators) {
        this.caveatEvaluators = caveatEvaluators;
    }

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        if (!context.hasAllow()) {
            return Optional.empty();
        }

        // Collect caveat definitions from runtime context (set by policy registry)
        Map<String, Object> caveatDefs = getCaveatDefinitions(context);

        if (caveatDefs.isEmpty()) {
            // No caveats — plain ALLOW
            return Optional.of(new DecisionOutcome(
                    "ALLOW",
                    "DECISION_POLICY_ALLOW",
                    "evidence://decision/policy-allow"
            ));
        }

        // Evaluate each registered caveat evaluator
        AttributeAccessMap combinedAccessMap = AttributeAccessMap.empty();

        for (CaveatEvaluator evaluator : caveatEvaluators) {
            if (!evaluator.evaluate(context, caveatDefs)) {
                // Caveat failed — narrow to DENY
                return Optional.of(new DecisionOutcome(
                        "DENY",
                        "DECISION_CAVEAT_FAILED",
                        "evidence://decision/caveat-failed"
                ));
            }

            // Apply field-level constraints from caveat
            AttributeAccessMap fieldConstraints = evaluator.applyFieldConstraints(
                    context.request().runtimeContext() == null ? java.util.Set.of()
                            : context.request().runtimeContext().keySet(),
                    caveatDefs
            );

            // Merge constraints using field-level override
            if (fieldConstraints != null && (!fieldConstraints.fieldAccess().isEmpty() || !fieldConstraints.tagAccess().isEmpty())) {
                combinedAccessMap = mergeAttributeMaps(combinedAccessMap, fieldConstraints);
            }
        }

        // ALLOW with caveat-enriched attribute access map
        return Optional.of(new DecisionOutcome(
                "ALLOW",
                "DECISION_POLICY_ALLOW_WITH_CAVEATS",
                "evidence://decision/policy-allow-caveats",
                combinedAccessMap
        ));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getCaveatDefinitions(DecisionContext context) {
        Object caveats = context.resolvedRuntimeContext().get("caveats");
        if (caveats instanceof Map) {
            return (Map<String, Object>) caveats;
        }
        return Map.of();
    }

    private AttributeAccessMap mergeAttributeMaps(AttributeAccessMap base, AttributeAccessMap overlay) {
        // Currently uses overlay's fieldAccess — the most restrictive caveat wins
        // This can be extended to merge multiple caveat constraints
        java.util.Map<String, AttributeAccessLevel> mergedFields = new java.util.HashMap<>(base.fieldAccess());
        mergedFields.putAll(overlay.fieldAccess());
        java.util.Map<String, AttributeAccessLevel> mergedTags = new java.util.HashMap<>(base.tagAccess());
        mergedTags.putAll(overlay.tagAccess());
        return new AttributeAccessMap(mergedFields, mergedTags);
    }
}
