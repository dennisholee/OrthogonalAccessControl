package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.port.out.ConditionEvaluatorPort;
import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Evaluates SpEL conditions on policies via the {@link ConditionEvaluatorPort}.
 */
public class SpelConditionRule implements DecisionRule {

    private final ConditionEvaluatorPort conditionEvaluator;

    public SpelConditionRule(ConditionEvaluatorPort conditionEvaluator) {
        this.conditionEvaluator = conditionEvaluator;
    }

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        for (String policy : context.matchedPolicies()) {
            String conditionExpr = extractCondition(policy);
            if (conditionExpr == null || conditionExpr.isBlank()) {
                continue;
            }

            ConditionEvaluatorPort.ConditionEvalContext evalContext = buildEvalContext(context);

            Optional<Boolean> result = conditionEvaluator.evaluate(conditionExpr, evalContext);

            if (result.isEmpty() || !result.get()) {
                String code = result.isEmpty() ? "SPEL_EVALUATION_ERROR" : "SPEL_CONDITION_FAILED";
                return Optional.of(new DecisionOutcome(
                        "DENY",
                        code,
                        "evidence://decision/spel/" + Integer.toHexString(conditionExpr.hashCode())
                ));
            }
        }

        return Optional.empty();
    }

    private ConditionEvaluatorPort.ConditionEvalContext buildEvalContext(DecisionContext context) {
        Map<String, Object> subjectAttrs = new LinkedHashMap<>();
        if (context.request().subject() != null) {
            subjectAttrs.put("id", context.request().subject().id());
            subjectAttrs.put("type", context.request().subject().type());
        }

        Map<String, Object> resourceAttrs = new LinkedHashMap<>();
        if (context.request().resource() != null) {
            resourceAttrs.put("id", context.request().resource().id());
            resourceAttrs.put("type", context.request().resource().type());
        }

        // Copy runtime context keys prefixed with "resource" into resourceAttrs
        // (e.g. "resource.requesterId" in SpEL expressions resolves via ResourceBean.getRequesterId())
        if (context.request().runtimeContext() != null) {
            for (var entry : context.request().runtimeContext().entrySet()) {
                String key = entry.getKey();
                // Map runtime context fields like requesterId directly to resource attributes
                if (key.endsWith("requesterId") || key.endsWith("RequesterId")) {
                    resourceAttrs.putIfAbsent("requesterId", entry.getValue());
                }
                // Map runtime context fields starting with "resource" into resourceAttrs
                if (key.startsWith("resource") && key.length() > 8) {
                    String attrName = Character.toLowerCase(key.charAt(8)) + key.substring(9);
                    resourceAttrs.putIfAbsent(attrName, entry.getValue());
                }
            }
        }

        // Environment attributes from runtime context + resolvedRuntimeContext
        Map<String, Object> envAttrs = new LinkedHashMap<>();
        if (context.request().runtimeContext() != null) {
            envAttrs.putAll(context.request().runtimeContext());
        }
        envAttrs.putAll(context.resolvedRuntimeContext());
        // Map currentHour to hour for SpEL expressions referencing environment.hour
        if (envAttrs.containsKey("currentHour") && !envAttrs.containsKey("hour")) {
            envAttrs.put("hour", envAttrs.get("currentHour"));
        }
        if (!envAttrs.containsKey("hour")) {
            envAttrs.put("hour", java.time.LocalTime.now().getHour());
        }
        // Map riskScore from runtime context
        if (envAttrs.containsKey("riskScore") && !(envAttrs.get("riskScore") instanceof Number)) {
            try { envAttrs.put("riskScore", Long.parseLong(envAttrs.get("riskScore").toString())); }
            catch (NumberFormatException ignored) {}
        }
        // Convert string numbers to integers for SpEL comparison operators
        for (Map.Entry<String, Object> entry : new LinkedHashMap<>(envAttrs).entrySet()) {
            String val = entry.getValue() != null ? entry.getValue().toString().trim() : "";
            if (!val.isEmpty()) {
                try { envAttrs.put(entry.getKey(), Long.parseLong(val)); }
                catch (NumberFormatException ignored) {}
            }
        }

        // Subject attributes from runtime context
        // Supports: subjectDepartment, subjectClearance, etc.
        if (context.request().runtimeContext() != null) {
            Object reqSubject = context.request().runtimeContext().get("subject");
            if (reqSubject instanceof Map<?, ?> sm) {
                sm.forEach((k, v) -> subjectAttrs.put(k.toString(), v));
            }
            // Also check for individual subject attribute keys (e.g. subjectDepartment)
            for (var entry : context.request().runtimeContext().entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("subject")) {
                    String attrName = key.substring("subject".length());
                    if (!attrName.isEmpty()) {
                        // Convert "Department" → "department"
                        attrName = Character.toLowerCase(attrName.charAt(0)) + attrName.substring(1);
                        subjectAttrs.putIfAbsent(attrName, entry.getValue());
                    }
                }
            }
        }

        // Merge boundary context into subject and resource
        if (context.request().boundaryContext() != null) {
            var bc = context.request().boundaryContext();
            subjectAttrs.put("market", bc.market());
            subjectAttrs.put("lob", bc.lineOfBusiness());
            subjectAttrs.put("geography", bc.geography());
            subjectAttrs.put("channel", bc.channel());
            subjectAttrs.put("tenant", bc.tenant());

            resourceAttrs.put("market", bc.market());
            resourceAttrs.put("lob", bc.lineOfBusiness());
            resourceAttrs.put("geography", bc.geography());
            resourceAttrs.put("channel", bc.channel());
            resourceAttrs.put("tenant", bc.tenant());
        }

        return new ConditionEvaluatorPort.ConditionEvalContext(
                subjectAttrs, resourceAttrs, envAttrs, context.request().action()
        );
    }

    /**
     * Extracts the SpEL condition from the matched policy string.
     *
     * <p>Format from MongoPolicyRegistryAdapter: {@code POL.ALLOW.POL.SPEL.NNNNN:subject.department == 'hr'}
     * Stored inline as: {@code POL.ALLOW.name:expression}
     * </p>
     */
    private String extractCondition(String policy) {
        if (policy == null || policy.isBlank()) return null;

        // Find the first colon. Everything before it is the policy label.
        // Everything after that contains dots, then a colon, then the expression.
        // Example: "POL.ALLOW.POL.SPEL.-12345:subject.department == 'hr'"
        // Split on ":" and look for our pattern
        String[] parts = policy.split(":", 2);
        if (parts.length < 2) return null;

        String expression = parts[1].trim();
        // Verify it's actually a SpEL expression (not a plain policy name)
        if (expression.startsWith("POL.") || expression.startsWith("FIELD.") 
            || expression.startsWith("REBAC.") || expression.startsWith("WORKLOAD.")
            || expression.startsWith("BREAK.") || expression.startsWith("E2E.")) {
            return null;
        }
        return expression;
    }
}