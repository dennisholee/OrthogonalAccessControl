package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;

import java.util.Optional;

public class ConsistencyTokenRule implements DecisionRule {

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        boolean strictConsistency = Boolean.TRUE.equals(context.request().strictConsistency());
        String requestToken = context.request().consistencyToken();

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

        if (strictConsistency && (requestToken == null || requestToken.isBlank())) {
            return Optional.of(new DecisionOutcome(
                    "DENY",
                    "DECISION_CONSISTENCY_TOKEN_REQUIRED",
                    "evidence://decision/consistency-token-required"
            ));
        }

        Object requiredToken = context.resolvedRuntimeContext().get("requiredConsistencyToken");
        if (!(requiredToken instanceof String required) || required.isBlank()) {
            if (strictConsistency) {
                return Optional.of(new DecisionOutcome(
                        "DENY",
                        "DECISION_CONSISTENCY_REFERENCE_UNAVAILABLE",
                        "evidence://decision/consistency-reference-unavailable"
                ));
            }
            return Optional.empty();
        }

        boolean mismatch = requestToken == null
                || requestToken.isBlank()
                || !requestToken.equals(required);

        if (!mismatch) {
            return Optional.empty();
        }

        return Optional.of(new DecisionOutcome(
                "DENY",
                "DECISION_CONSISTENCY_TOKEN_MISMATCH",
                "evidence://decision/consistency-token-mismatch"
        ));
    }

    private Number asNumber(Object value) {
        return value instanceof Number number ? number : null;
    }
}