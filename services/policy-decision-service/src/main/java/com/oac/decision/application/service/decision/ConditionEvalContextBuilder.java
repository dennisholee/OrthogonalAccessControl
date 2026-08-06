package com.oac.decision.application.service.decision;

import com.oac.decision.application.port.out.ConditionEvaluatorPort;
import com.oac.decision.application.service.decision.DecisionContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the {@link ConditionEvaluatorPort.ConditionEvalContext} consumed by SpEL
 * conditions, from a {@link DecisionContext}.
 * <p>
 * Single source of truth for the runtime-context → subject/resource/environment
 * attribute mapping (e.g. {@code subjectDepartment} → {@code subject.department},
 * {@code requesterId} → {@code resource.requesterId}). Rules evaluating conditions
 * MUST delegate here instead of re-implementing the mapping.
 */
public final class ConditionEvalContextBuilder {

    private ConditionEvalContextBuilder() {
    }

    public static ConditionEvaluatorPort.ConditionEvalContext build(DecisionContext context) {
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

        Map<String, Object> runtimeContext = context.request().runtimeContext();
        if (runtimeContext != null) {
            // Subject attribute map (subject: { department: "compliance", ... })
            Object reqSubject = runtimeContext.get("subject");
            if (reqSubject instanceof Map<?, ?> subjectMap) {
                subjectMap.forEach((k, v) -> subjectAttrs.put(k.toString(), v));
            }

            for (var entry : runtimeContext.entrySet()) {
                String key = entry.getKey();
                // requesterId → resource.requesterId
                if (key.endsWith("requesterId") || key.endsWith("RequesterId")) {
                    resourceAttrs.putIfAbsent("requesterId", entry.getValue());
                }
                // resource* keys → resource attributes (camelCase)
                if (key.startsWith("resource") && key.length() > "resource".length()) {
                    String attrName = Character.toLowerCase(key.charAt("resource".length())) + key.substring("resource".length() + 1);
                    resourceAttrs.putIfAbsent(attrName, entry.getValue());
                }
                // subject* keys → subject attributes (camelCase)
                if (key.startsWith("subject") && key.length() > "subject".length()) {
                    String attrName = Character.toLowerCase(key.charAt("subject".length())) + key.substring("subject".length() + 1);
                    subjectAttrs.putIfAbsent(attrName, entry.getValue());
                }
            }
        }

        // Environment attributes from runtime context + resolved runtime context,
        // with the same coercions the original SpelConditionRule applied.
        Map<String, Object> envAttrs = new LinkedHashMap<>();
        if (context.request().runtimeContext() != null) {
            envAttrs.putAll(context.request().runtimeContext());
        }
        if (context.resolvedRuntimeContext() != null) {
            envAttrs.putAll(context.resolvedRuntimeContext());
        }
        // Map currentHour to hour for SpEL expressions referencing environment.hour
        if (envAttrs.containsKey("currentHour") && !envAttrs.containsKey("hour")) {
            envAttrs.put("hour", envAttrs.get("currentHour"));
        }
        if (!envAttrs.containsKey("hour")) {
            envAttrs.put("hour", java.time.LocalTime.now().getHour());
        }
        // Map riskScore from runtime context
        if (envAttrs.containsKey("riskScore") && !(envAttrs.get("riskScore") instanceof Number)) {
            try {
                envAttrs.put("riskScore", Long.parseLong(envAttrs.get("riskScore").toString()));
            } catch (NumberFormatException ignored) {
            }
        }
        // Convert string numbers to integers for SpEL comparison operators
        for (Map.Entry<String, Object> entry : new LinkedHashMap<>(envAttrs).entrySet()) {
            String val = entry.getValue() != null ? entry.getValue().toString().trim() : "";
            if (!val.isEmpty()) {
                try {
                    envAttrs.put(entry.getKey(), Long.parseLong(val));
                } catch (NumberFormatException ignored) {
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

        // Convert principalMemberships into a Map for SpEL access (#principalMemberships.tenants)
        Map<String, Object> membershipAttrs = new LinkedHashMap<>();
        if (context.request().principalMemberships() != null) {
            var pm = context.request().principalMemberships();
            membershipAttrs.put("tenants", pm.tenants());
            membershipAttrs.put("geographies", pm.geographies());
            membershipAttrs.put("markets", pm.markets());
            membershipAttrs.put("linesOfBusiness", pm.linesOfBusiness());
            membershipAttrs.put("channels", pm.channels());
            membershipAttrs.put("purposes", pm.purposes());
            membershipAttrs.put("regulatoryRegimes", pm.regulatoryRegimes());
        }

        return new ConditionEvaluatorPort.ConditionEvalContext(
                subjectAttrs, resourceAttrs, envAttrs, context.request().action(),
                membershipAttrs
        );
    }
}
