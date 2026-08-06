package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.port.out.ConditionEvaluatorPort;
import com.oac.decision.application.port.out.RelationshipGraphPort;
import com.oac.decision.application.service.decision.ConditionEvalContextBuilder;
import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;

import java.util.Map;
import java.util.Optional;

/**
 * Evaluates the typed {@code conditions[]} array attached to a policy document.
 * <p>
 * The Mongo adapter renders each condition into the matched-policy entry using
 * the marker format {@code :COND.<type>=<value>} with {@code |} as the segment
 * delimiter. All conditions are AND-ed — if any fails, the rule denies.
 * <p>
 * Supported condition types: {@code spel}, {@code time}, {@code ip}, {@code rebac}.
 */
public class ConditionCompositionRule implements DecisionRule {

    private static final String MARKER = ":COND.";

    private final ConditionEvaluatorPort conditionEvaluator;
    private final RelationshipGraphPort relationshipGraphPort;

    public ConditionCompositionRule(
            ConditionEvaluatorPort conditionEvaluator,
            RelationshipGraphPort relationshipGraphPort
    ) {
        this.conditionEvaluator = conditionEvaluator;
        this.relationshipGraphPort = relationshipGraphPort;
    }

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        for (String policy : context.matchedPolicies()) {
            if (policy == null || !policy.contains(MARKER)) {
                continue;
            }
            String suffix = policy.substring(policy.indexOf(MARKER));
            for (String segment : suffix.split("\\|")) {
                Optional<DecisionOutcome> failure = evaluateSegment(segment.trim(), context);
                if (failure.isPresent()) {
                    return failure;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<DecisionOutcome> evaluateSegment(String segment, DecisionContext context) {
        if (!segment.startsWith(MARKER)) {
            return Optional.empty();
        }
        String body = segment.substring(MARKER.length());
        int eq = body.indexOf('=');
        if (eq <= 0) {
            return Optional.empty();
        }
        String type = body.substring(0, eq);
        String value = body.substring(eq + 1);

        return switch (type) {
            case "spel" -> evaluateSpel(value, context);
            case "time" -> evaluateTimeWindow(value, context);
            case "ip" -> evaluateSourceIp(value, context);
            case "rebac" -> evaluateRebac(value, context);
            default -> Optional.empty();
        };
    }

    private Optional<DecisionOutcome> evaluateSpel(String expression, DecisionContext context) {
        ConditionEvaluatorPort.ConditionEvalContext evalContext = ConditionEvalContextBuilder.build(context);
        Optional<Boolean> result = conditionEvaluator.evaluate(expression, evalContext);
        if (result.isEmpty() || !result.get()) {
            String code = result.isEmpty() ? "SPEL_EVALUATION_ERROR" : "SPEL_CONDITION_FAILED";
            return Optional.of(new DecisionOutcome(
                    "DENY",
                    code,
                    "evidence://decision/condition/spel/" + Integer.toHexString(expression.hashCode())
            ));
        }
        return Optional.empty();
    }

    private Optional<DecisionOutcome> evaluateTimeWindow(String value, DecisionContext context) {
        // value format: "09:00-17:00 UTC"
        String[] parts = value.split("\\s+");
        if (parts.length < 1) {
            return Optional.empty();
        }
        String[] bounds = parts[0].split("-");
        if (bounds.length != 2) {
            return Optional.empty();
        }
        Integer startHour = parseHour(bounds[0]);
        Integer endHour = parseHour(bounds[1]);
        Integer currentHour = currentHour(context);
        if (startHour == null || endHour == null || currentHour == null) {
            return Optional.empty();
        }
        boolean within = currentHour >= startHour && currentHour < endHour;
        if (!within) {
            return Optional.of(new DecisionOutcome(
                    "DENY",
                    "DECISION_TIME_WINDOW_VIOLATION",
                    "evidence://decision/condition/time-window"
            ));
        }
        return Optional.empty();
    }

    private Optional<DecisionOutcome> evaluateSourceIp(String cidr, DecisionContext context) {
        String sourceIp = stringParam(context.resolvedRuntimeContext(), "sourceIp");
        if (sourceIp == null) {
            sourceIp = stringParam(context.resolvedRuntimeContext(), "clientIp");
        }
        if (sourceIp == null) {
            return Optional.empty();
        }
        if (!inCidr(sourceIp, cidr)) {
            return Optional.of(new DecisionOutcome(
                    "DENY",
                    "DECISION_SOURCE_IP_VIOLATION",
                    "evidence://decision/condition/source-ip"
            ));
        }
        return Optional.empty();
    }

    private Optional<DecisionOutcome> evaluateRebac(String relationshipType, DecisionContext context) {
        boolean hasRelationship = relationshipGraphPort.hasRelationship(
                context.request().subject().id(),
                context.request().resource().id(),
                relationshipType,
                relationshipGraphPort.getMaxTraversalDepth()
        );
        if (!hasRelationship) {
            return Optional.of(new DecisionOutcome(
                    "DENY",
                    "DECISION_REBAC_NO_RELATIONSHIP",
                    "evidence://decision/condition/rebac"
            ));
        }
        return Optional.empty();
    }

    private Integer currentHour(DecisionContext context) {
        Object value = context.resolvedRuntimeContext().get("currentHour");
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer parseHour(String hhmm) {
        try {
            return Integer.parseInt(hhmm.split(":")[0]);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean inCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String network = parts[0];
            int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : 32;
            long ipLong = ipToLong(ip);
            long networkLong = ipToLong(network);
            long mask = prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
            return (ipLong & mask) == (networkLong & mask);
        } catch (Exception e) {
            return false;
        }
    }

    private long ipToLong(String ip) {
        String[] octets = ip.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) + Integer.parseInt(octets[i]);
        }
        return result;
    }

    private String stringParam(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object val = map.get(key);
        return val == null ? null : val.toString();
    }


}
