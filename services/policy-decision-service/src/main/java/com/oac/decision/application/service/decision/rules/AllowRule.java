package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;

import java.util.Optional;

public class AllowRule implements DecisionRule {

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        if (!context.hasAllow()) {
            return Optional.empty();
        }

        return Optional.of(new DecisionOutcome(
                "ALLOW",
                "DECISION_POLICY_ALLOW",
                "evidence://decision/policy-allow"
        ));
    }
}