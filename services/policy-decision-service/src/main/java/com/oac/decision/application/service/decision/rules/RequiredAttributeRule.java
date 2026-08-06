package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.port.out.AttributeSchemaRegistryPort;
import com.oac.decision.application.port.out.ConditionEvaluatorPort;
import com.oac.decision.application.port.shared.AttributeReferenceExtractor;
import com.oac.decision.application.service.decision.ConditionEvalContextBuilder;
import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;
import com.oac.decision.model.AttributeSchema;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Attribute Schema Registry enforcement (Section 4.7) at evaluation time.
 * <p>
 * When a matched policy's SpEL condition references an attribute declared
 * {@code isRequired} in the registry, that attribute MUST resolve to a non-null value
 * in the evaluation context. If it is missing, the PDP denies with
 * {@code DECISION_MISSING_ATTRIBUTE} — preventing a null-comparison typo from silently
 * over-granting.
 * <p>
 * Evaluated immediately before {@code SpelConditionRule} (priority 7). Policies that
 * carry no inline SpEL expression, or reference only optional attributes, pass through.
 */
public class RequiredAttributeRule implements DecisionRule {

    private final AttributeSchemaRegistryPort schemaRegistry;

    public RequiredAttributeRule(AttributeSchemaRegistryPort schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        for (String policy : context.matchedPolicies()) {
            String expression = AttributeReferenceExtractor.extractSpelExpression(policy);
            if (expression == null) {
                continue;
            }
            ConditionEvaluatorPort.ConditionEvalContext evalContext = ConditionEvalContextBuilder.build(context);
            for (String ref : AttributeReferenceExtractor.extract(expression)) {
                Optional<AttributeSchema> schema = schemaRegistry.find(ref);
                if (schema.isPresent() && schema.get().isRequired() && !resolved(evalContext, ref)) {
                    return Optional.of(new DecisionOutcome(
                            "DENY",
                            "DECISION_MISSING_ATTRIBUTE",
                            "evidence://decision/schema/" + ref
                    ));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves a canonical reference (e.g. {@code resource.dataSubjectCategory})
     * against the evaluation context and reports whether a non-null value is present.
     */
    private boolean resolved(ConditionEvaluatorPort.ConditionEvalContext ctx, String ref) {
        int dot = ref.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        String root = ref.substring(0, dot);
        String path = ref.substring(dot + 1);
        Map<String, Object> attrs = switch (root) {
            case "subject" -> ctx.subject();
            case "resource" -> ctx.resource();
            case "environment" -> ctx.environment();
            case "principalMemberships" -> ctx.principalMemberships();
            default -> Map.of();
        };
        return attrs.get(path) != null;
    }
}
