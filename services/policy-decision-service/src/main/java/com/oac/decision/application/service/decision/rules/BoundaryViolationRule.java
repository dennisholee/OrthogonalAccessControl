package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;

import java.util.List;
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
        ) || (bc.purpose() != null && !bc.purpose().isBlank() && hasMismatch(
                bc.purpose(),
                context.resolvedRuntimeContext().get("resourcePurpose")
        )) || (bc.regulatoryRegime() != null && !bc.regulatoryRegime().isBlank() && hasMismatch(
                bc.regulatoryRegime(),
                context.resolvedRuntimeContext().get("resourceRegulatoryRegime")
        ));

        if (!hasBoundaryViolation) {
            return Optional.empty();
        }

        // Cross-domain authorisation (Section 4.33): when the request boundary differs from
        // the resource boundary AND the caller provides a valid justification AND declares
        // principalMemberships, defer to DomainMembershipRule which validates that the
        // principal is a member of both the request domain and the resource domain.
        String justification = context.request().crossBoundaryJustification();
        boolean hasValidJustification = justification != null && !justification.isBlank()
                && justification.length() >= 10;
        if (hasValidJustification && context.request().principalMemberships() != null) {
            return Optional.empty();
        }

        return Optional.of(new DecisionOutcome(
                "DENY",
                "DECISION_BOUNDARY_DENY",
                "evidence://decision/boundary-deny"
        ));
    }

    private boolean hasMismatch(String expectedBoundaryValue, Object runtimeValue) {
        if (runtimeValue == null) return false;

        // Multi-value array scoping (IN semantics) — see docs/POLICY_ARCHITECTURE.md Section 4.32.
        if (runtimeValue instanceof List<?> list) {
            // Request boundary value is in the list — no violation.
            for (Object item : list) {
                if (item instanceof String s && s.equals(expectedBoundaryValue)) {
                    return false;
                }
            }
            // Wildcard "*" matches anything
            if ("*".equals(expectedBoundaryValue)) return false;
            return true;
        }

        if (!(runtimeValue instanceof String value)) return false;
        // Wildcard "*" matches anything
        if ("*".equals(expectedBoundaryValue)) return false;
        return !expectedBoundaryValue.equals(value);
    }
}