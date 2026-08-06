package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;

import java.util.Map;
import java.util.Optional;

/**
 * SuppressionRule — evaluated at priority 2, directly after ExplicitDenyRule.
 * <p>
 * Suppression lists (DNC, DNS, CONTROL_GROUP, LITIGATION_HOLD, DECEASED) override
 * ALL marketing entitlements regardless of consent or policy. Suppression evaluation
 * occurs before any Allow, boundary, or consent evaluation — suppression cannot be
 * overridden by any lower-priority rule.
 * <p>
 * See docs/POLICY_ARCHITECTURE.md Section 4.26.
 */
public class SuppressionRule implements DecisionRule {

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        Map<String, Boolean> flags = context.request().suppressionFlags();
        if (flags == null || flags.isEmpty()) {
            return Optional.empty();
        }

        String action = context.request().action();

        boolean dncActive = Boolean.TRUE.equals(flags.get("DNC"));
        boolean dnsActive = Boolean.TRUE.equals(flags.get("DNS"));
        boolean controlGroupActive = Boolean.TRUE.equals(flags.get("CONTROL_GROUP"));
        boolean litigationHoldActive = Boolean.TRUE.equals(flags.get("LITIGATION_HOLD"));
        boolean deceasedActive = Boolean.TRUE.equals(flags.get("DECEASED"));

        boolean suppressed = false;
        if (dncActive && isMarketingContactAction(action)) suppressed = true;
        if (dnsActive && isSaleOrShareAction(action)) suppressed = true;
        if (controlGroupActive && isCampaignInclusionAction(action)) suppressed = true;
        if (litigationHoldActive && !isLegalOrDsarAction(action)) suppressed = true;
        if (deceasedActive && (isMarketingContactAction(action) || isAnalyticsAction(action))) suppressed = true;

        if (!suppressed) {
            return Optional.empty();
        }

        return Optional.of(new DecisionOutcome(
                "DENY",
                "DECISION_SUPPRESSED",
                "evidence://decision/suppressed"
        ));
    }

    private boolean isMarketingContactAction(String action) {
        if (action == null || action.isBlank()) return false;
        String a = action.toLowerCase();
        return a.contains("contact")
                || a.contains("marketing")
                || a.contains("campaign");
    }

    private boolean isSaleOrShareAction(String action) {
        if (action == null || action.isBlank()) return false;
        String a = action.toLowerCase();
        return a.contains("sell")
                || a.contains("share")
                || a.contains("sale")
                || a.contains("activate")
                || a.contains("export");
    }

    private boolean isCampaignInclusionAction(String action) {
        if (action == null || action.isBlank()) return false;
        String a = action.toLowerCase();
        return a.contains("campaign")
                || a.contains("activate")
                || a.contains("contact");
    }

    private boolean isAnalyticsAction(String action) {
        if (action == null || action.isBlank()) return false;
        String a = action.toLowerCase();
        return a.contains("analytics")
                || a.contains("read_aggregate");
    }

    private boolean isLegalOrDsarAction(String action) {
        if (action == null || action.isBlank()) return false;
        String a = action.toLowerCase();
        return a.contains("legal")
                || a.contains("dsar")
                || a.contains("erase")
                || a.contains("delete");
    }
}