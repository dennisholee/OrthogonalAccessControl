package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;
import com.oac.decision.model.CheckPermissionRequest;

import java.util.Map;
import java.util.Optional;

/**
 * CrossBoundaryRule — evaluated at priority 3, between SuppressionRule and MissingBoundaryContextRule.
 * <p>
 * Detects when request boundary values differ from resource boundary values AND the caller
 * explicitly declares a cross-boundary intent by providing a {@code crossBoundaryJustification}
 * field in the request.
 * <p>
 * When a cross-boundary condition exists and the justification is empty or invalid, the rule
 * denies with {@code DECISION_CROSS_BOUNDARY_NO_JUSTIFICATION}.
 * <p>
 * When {@code crossBoundaryJustification} is absent (null), the rule does not fire — downstream
 * rules such as BoundaryViolationRule handle the mismatch instead.
 * <p>
 * See docs/POLICY_ARCHITECTURE.md Section 4.10.
 */
public class CrossBoundaryRule implements DecisionRule {

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        var request = context.request();
        var bc = request.boundaryContext();
        if (bc == null) {
            return Optional.empty();
        }

        // Only fire when the caller explicitly declares cross-boundary intent.
        // This preserves the existing boundary-violation semantics (DECISION_BOUNDARY_DENY)
        // for requests that don't carry crossBoundaryJustification.
        String justification = request.crossBoundaryJustification();
        if (justification == null) {
            return Optional.empty();
        }

        boolean hasCrossBoundary = hasCrossBoundaryMismatch(request);
        if (!hasCrossBoundary) {
            return Optional.empty();
        }

        // Cross-boundary access requires explicit justification
        boolean hasValidJustification = !justification.isBlank()
                && justification.length() >= 10;

        if (!hasValidJustification) {
            return Optional.of(new DecisionOutcome(
                    "DENY",
                    "DECISION_CROSS_BOUNDARY_NO_JUSTIFICATION",
                    "evidence://decision/cross-boundary-no-justification"
            ));
        }

        // A valid justification was provided — let downstream rules proceed.
        return Optional.empty();
    }

    private boolean hasCrossBoundaryMismatch(CheckPermissionRequest request) {
        var bc = request.boundaryContext();
        if (bc == null) return false;

        Map<String, Object> rt = request.runtimeContext() == null ? Map.of() : request.runtimeContext();

        Object resourceTenant = rt.get("resourceTenant");
        Object resourceGeography = rt.get("resourceGeography");
        Object resourceMarket = rt.get("resourceMarket");
        Object resourceLob = rt.get("resourceLineOfBusiness");
        Object resourceChannel = rt.get("resourceChannel");
        Object resourcePurpose = rt.get("resourcePurpose");
        Object resourceRegime = rt.get("resourceRegulatoryRegime");

        return mismatch(bc.tenant(), resourceTenant)
                || mismatch(bc.geography(), resourceGeography)
                || mismatch(bc.market(), resourceMarket)
                || mismatch(bc.lineOfBusiness(), resourceLob)
                || mismatch(bc.channel(), resourceChannel)
                || mismatch(bc.purpose(), resourcePurpose)
                || mismatch(bc.regulatoryRegime(), resourceRegime);
    }

    private boolean mismatch(String expected, Object actual) {
        if (actual == null) return false;
        if (!(actual instanceof String actualStr)) return false;
        // Wildcard or absent expected means no boundary constraint
        if (expected == null || expected.isBlank() || "*".equals(expected)) return false;
        return !expected.equals(actualStr);
    }
}