package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;

import java.util.Optional;

public class BoundaryViolationRule implements DecisionRule {

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        if (!context.hasAllow()) {
            return Optional.empty();
        }

        boolean hasBoundaryViolation = hasMismatch(
                context.request().boundaryContext().tenant(),
                context.resolvedRuntimeContext().get("resourceTenant")
        ) || hasMismatch(
                context.request().boundaryContext().geography(),
                context.resolvedRuntimeContext().get("resourceGeography")
        ) || hasMismatch(
                context.request().boundaryContext().market(),
                context.resolvedRuntimeContext().get("resourceMarket")
        ) || hasMismatch(
                context.request().boundaryContext().lineOfBusiness(),
                context.resolvedRuntimeContext().get("resourceLineOfBusiness")
        ) || hasMismatch(
                context.request().boundaryContext().channel(),
                context.resolvedRuntimeContext().get("resourceChannel")
        );

        if (!hasBoundaryViolation) {
            return Optional.empty();
        }

        return Optional.of(new DecisionOutcome(
                "DENY",
                "DECISION_BOUNDARY_DENY",
                "evidence://decision/boundary-deny"
        ));
    }

    private boolean hasMismatch(String expectedBoundaryValue, Object runtimeValue) {
        return runtimeValue instanceof String value && !expectedBoundaryValue.equals(value);
    }
}