package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;

import java.util.Optional;

public class DefaultDenyRule implements DecisionRule {

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        return Optional.of(new DecisionOutcome(
                "DENY",
                "DECISION_DEFAULT_DENY",
                "evidence://decision/default-deny"
        ));
    }
}