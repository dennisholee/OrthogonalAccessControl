package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;

import java.util.Optional;

public class DependencyOutageRule implements DecisionRule {

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        Object dependencyHealthy = context.resolvedRuntimeContext().get("dependencyHealthy");
        if (!Boolean.FALSE.equals(dependencyHealthy)) {
            return Optional.empty();
        }

        if (Boolean.TRUE.equals(context.request().strictConsistency())) {
            return Optional.of(new DecisionOutcome(
                    "DENY",
                    "DECISION_FAIL_CLOSED_STRICT_CONSISTENCY",
                    "evidence://decision/fail-closed-strict-consistency"
            ));
        }

        String classification = context.request().endpointClassification();
        if (!"FAIL_OPEN".equals(classification)) {
            return Optional.of(new DecisionOutcome(
                    "DENY",
                    "DECISION_FAIL_CLOSED_DEPENDENCY_OUTAGE",
                    "evidence://decision/fail-closed-dependency-outage"
            ));
        }

                if (!Boolean.TRUE.equals(context.resolvedRuntimeContext().get("failOpenEndpointApproved"))) {
                    return Optional.of(new DecisionOutcome(
                        "DENY",
                        "DECISION_FAIL_OPEN_ENDPOINT_NOT_APPROVED",
                        "evidence://decision/fail-open-endpoint-not-approved"
                    ));
                }

        boolean eligible = "read".equalsIgnoreCase(context.request().action())
                && Boolean.TRUE.equals(context.resolvedRuntimeContext().get("failOpenReadOnly"))
                && Boolean.TRUE.equals(context.resolvedRuntimeContext().get("failOpenNonSensitive"))
                && Boolean.TRUE.equals(context.resolvedRuntimeContext().get("failOpenBoundarySafe"));

        if (!eligible) {
            return Optional.of(new DecisionOutcome(
                    "DENY",
                    "DECISION_FAIL_OPEN_CLASSIFICATION_VIOLATION",
                    "evidence://decision/fail-open-classification-violation"
            ));
        }

        return Optional.of(new DecisionOutcome(
                "ALLOW",
                "DECISION_FAIL_OPEN_ALLOWED",
                "evidence://decision/fail-open-allowed"
        ));
    }
}
