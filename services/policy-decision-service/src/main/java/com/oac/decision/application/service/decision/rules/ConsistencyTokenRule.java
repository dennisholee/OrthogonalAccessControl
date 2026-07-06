package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;

import java.util.Optional;

public class ConsistencyTokenRule implements DecisionRule {

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
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
        boolean mismatch = !hasRequestToken || !requestToken.equals(requiredToken);

        if (!mismatch) {
            return Optional.empty();
        }

        // When a token is provided but doesn't match the required token, it's stale.
        // From the system's perspective, no valid token was provided, so return
        // TOKEN_REQUIRED (the appropriate code for a stale/expired token).
        return Optional.of(new DecisionOutcome(
                "DENY",
                "DECISION_CONSISTENCY_TOKEN_REQUIRED",
                "evidence://decision/consistency-token-stale"
        ));
    }

    private Number asNumber(Object value) {
        return value instanceof Number number ? number : null;
    }
}