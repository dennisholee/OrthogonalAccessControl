package com.oac.decision.application.service.decision;

import java.util.Optional;

public interface DecisionRule {

    Optional<DecisionOutcome> evaluate(DecisionContext context);
}