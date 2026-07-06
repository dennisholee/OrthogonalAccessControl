package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;

import java.util.Optional;

public class ExplicitDenyRule implements DecisionRule {

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        boolean hasExplicitDeny = context.matchedPolicies().stream().anyMatch(policy -> policy.contains("DENY"));
        if (!hasExplicitDeny) {
            return Optional.empty();
        }

        return Optional.of(new DecisionOutcome(
                "DENY",
                "DECISION_EXPLICIT_DENY",
                "evidence://decision/explicit-deny"
        ));
    }
}