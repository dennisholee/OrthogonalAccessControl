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

        var bc = context.request().boundaryContext();
        if (bc == null) {
            return Optional.empty();
        }

        boolean hasBoundaryViolation = hasMismatch(
                bc.tenant(),
                context.resolvedRuntimeContext().get("resourceTenant")
        ) || hasMismatch(
                bc.geography(),
                context.resolvedRuntimeContext().get("resourceGeography")
        ) || hasMismatch(
                bc.market(),
                context.resolvedRuntimeContext().get("resourceMarket")
        ) || hasMismatch(
                bc.lineOfBusiness(),
                context.resolvedRuntimeContext().get("resourceLineOfBusiness")
        ) || hasMismatch(
                bc.channel(),
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
        if (!(runtimeValue instanceof String value)) return false;
        // Wildcard "*" matches anything
        if ("*".equals(expectedBoundaryValue)) return false;
        return !expectedBoundaryValue.equals(value);
    }
}