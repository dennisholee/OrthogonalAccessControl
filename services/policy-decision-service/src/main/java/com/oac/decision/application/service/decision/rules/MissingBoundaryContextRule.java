package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;

import java.util.Optional;
import java.util.stream.Stream;

public class MissingBoundaryContextRule implements DecisionRule {

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        var bc = context.request().boundaryContext();
        
        // Special case: boundary context is explicitly null (missing).
        // This handles the test scenario where boundary is omitted entirely.
        // Only DENY here if there's Allow context (matched policies include ALLOW or break-glass).
        if (bc == null) {
            if (context.hasAllow()) {
                return Optional.of(new DecisionOutcome(
                        "DENY",
                        "DECISION_MISSING_BOUNDARY_CONTEXT",
                        "evidence://decision/missing-boundary-context"
                ));
            }
            // No allow context — let downstream rules handle it
            return Optional.empty();
        }

        if (!context.hasAllow()) {
            return Optional.empty();
        }

        boolean hasMissingBoundaryContext = Stream.of(
                        "resourceTenant",
                        "resourceGeography",
                        "resourceMarket",
                        "resourceLineOfBusiness",
                        "resourceChannel"
                )
                .anyMatch(key -> !hasUsableString(context.resolvedRuntimeContext().get(key)));

        if (!hasMissingBoundaryContext) {
            return Optional.empty();
        }

        return Optional.of(new DecisionOutcome(
                "DENY",
                "DECISION_MISSING_BOUNDARY_CONTEXT",
                "evidence://decision/missing-boundary-context"
        ));
    }

    private boolean hasUsableString(Object value) {
        return value instanceof String text && !text.isBlank();
    }
}