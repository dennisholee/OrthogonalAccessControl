package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;

import java.util.Map;
import java.util.Optional;

public class ConsistencyTokenRule implements DecisionRule {

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        // Per-scope token vector (Section 4.6 consistency token partitioning):
        // when the resolved context carries a map of required tokens per scope, compare
        // each scope independently against the request's token vector.
        @SuppressWarnings("unchecked")
        Map<String, String> requiredByScope = (Map<String, String>) context.resolvedRuntimeContext()
                .get("requiredConsistencyTokens");
        if (requiredByScope != null && !requiredByScope.isEmpty()) {
            Map<String, String> requestByScope = context.request().consistencyTokens();
            if (requestByScope == null || requestByScope.isEmpty()) {
                return Optional.of(new DecisionOutcome(
                        "DENY",
                        "DECISION_CONSISTENCY_TOKEN_REQUIRED",
                        "evidence://decision/consistency-token-required"
                ));
            }
            for (Map.Entry<String, String> entry : requiredByScope.entrySet()) {
                String scope = entry.getKey();
                String required = entry.getValue();
                if (required == null || required.isBlank()) continue;
                String requestToken = requestByScope.get(scope);
                if (requestToken == null || requestToken.isBlank() || !requestToken.equals(required)) {
                    return Optional.of(new DecisionOutcome(
                            "DENY",
                            "CONSISTENCY_VIOLATION",
                            "evidence://decision/consistency-token-stale/" + scope
                    ));
                }
            }
            return Optional.empty();
        }

        // Legacy single-token logic
        String requestToken = context.request().consistencyToken();
        Object requiredToken = context.resolvedRuntimeContext().get("requiredConsistencyToken");

        // If no token is provided and no required token is set, skip this rule
        if ((requestToken == null || requestToken.isBlank()) && requiredToken == null) {
            return Optional.empty();
        }

        // If we have a required token but no request token, it's required
        boolean strictConsistency = Boolean.TRUE.equals(context.request().strictConsistency());
        boolean hasRequestToken = requestToken != null && !requestToken.isBlank();
        boolean hasRequiredToken = requiredToken instanceof String req && !req.isBlank();

        if (hasRequiredToken && !hasRequestToken) {
            return Optional.of(new DecisionOutcome(
                    "DENY",
                    "DECISION_CONSISTENCY_TOKEN_REQUIRED",
                    "evidence://decision/consistency-token-required"
            ));
        }

        if (strictConsistency) {
            Number regionalLagMs = asNumber(context.resolvedRuntimeContext().get("simulatedRegionalLagMs"));
            if (regionalLagMs != null && regionalLagMs.intValue() > 250) {
                return Optional.of(new DecisionOutcome(
                        "DENY",
                        "DECISION_REGIONAL_REPLICA_LAG",
                        "evidence://decision/regional-replica-lag"
                ));
            }

            Number replicaVersion = asNumber(context.resolvedRuntimeContext().get("replicaVersion"));
            Number minimumReplicaVersion = asNumber(context.resolvedRuntimeContext().get("minimumReplicaVersion"));
            if (replicaVersion != null && minimumReplicaVersion != null
                    && replicaVersion.longValue() < minimumReplicaVersion.longValue()) {
                return Optional.of(new DecisionOutcome(
                        "DENY",
                        "DECISION_REGIONAL_REPLICA_VERSION_STALE",
                        "evidence://decision/regional-replica-version-stale"
                ));
            }
        }

        // If request token is provided but no required token, pass through
        if (!hasRequiredToken) {
            return Optional.empty();
        }

        // Check mismatch between request token and required token
        if (requestToken != null && requestToken.equals(requiredToken)) {
            // Token matches — pass through (ALLOW, let AllowRule handle)
            return Optional.empty();
        }

        // Token provided but doesn't match required — it's stale/expired
        // Return CONSISTENCY_VIOLATION as the test expects
        return Optional.of(new DecisionOutcome(
                "DENY",
                "CONSISTENCY_VIOLATION",
                "evidence://decision/consistency-token-stale"
        ));
    }

    private Number asNumber(Object value) {
        return value instanceof Number number ? number : null;
    }
}