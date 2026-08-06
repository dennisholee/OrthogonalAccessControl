package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;

import java.util.Map;
import java.util.Optional;

/**
 * SubjectConsentRule — evaluated at priority 8, between SpelConditionRule and ConsistencyTokenRule.
 * <p>
 * Validates per-purpose consent attributes against the decision request's consent
 * version and consent attributes. The rule handles the four consent outcomes
 * described in docs/POLICY_ARCHITECTURE.md Section 4.19:
 * GRANTED / WITHDRAWN / NOT_PROVIDED / EXPIRED.
 * <p>
 * Decision codes:
 * <ul>
 *   <li>{@code DECISION_CONSENT_REQUIRED} — consent version mismatch, requires re-fetch</li>
 *   <li>{@code DECISION_CONSENT_WITHDRAWN} — consent withdrawn or not provided</li>
 *   <li>{@code DECISION_CONSENT_EXPIRED} — consent has exceeded its max age</li>
 *   <li>{@code DECISION_OBJECTION_SUSTAINED} — GDPR Article 21 objection sustained</li>
 * </ul>
 */
public class SubjectConsentRule implements DecisionRule {

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        Map<String, Object> consentAttributes = context.request().consentAttributes();
        String consentVersion = context.request().consentVersion();

        // No consent attributes required by the request — consent evaluation does not apply
        if (consentAttributes == null || consentAttributes.isEmpty()) {
            return Optional.empty();
        }

        boolean hasConsentVersion = consentVersion != null && !consentVersion.isBlank();

        // If consent attributes are required but version is absent → consent required
        if (!hasConsentVersion) {
            return Optional.of(new DecisionOutcome(
                    "DENY",
                    "DECISION_CONSENT_REQUIRED",
                    "evidence://decision/consent-required"
            ));
        }

        // Evaluate each required consent attribute
        for (Map.Entry<String, Object> entry : consentAttributes.entrySet()) {
            String attributeName = entry.getKey();
            Object attributeValue = entry.getValue();

            // Consent attributes can be a simple status string, or a nested object
            // with additional metadata (grantedAt, expiresAt, status, etc.)
            String status = extractStatus(attributeValue);
            if (status == null) {
                continue;
            }

            switch (status) {
                case "WITHDRAWN", "NOT_PROVIDED" -> {
                    return Optional.of(new DecisionOutcome(
                            "DENY",
                            "DECISION_CONSENT_WITHDRAWN",
                            "evidence://decision/consent-withdrawn/" + attributeName
                    ));
                }
                case "EXPIRED" -> {
                    return Optional.of(new DecisionOutcome(
                            "DENY",
                            "DECISION_CONSENT_EXPIRED",
                            "evidence://decision/consent-expired/" + attributeName
                    ));
                }
                case "OBJECTION_SUSTAINED" -> {
                    return Optional.of(new DecisionOutcome(
                            "DENY",
                            "DECISION_OBJECTION_SUSTAINED",
                            "evidence://decision/objection-sustained/" + attributeName
                    ));
                }
                case "GRANTED", "LEGITIMATE_INTEREST", "LEGAL_OBLIGATION",
                     "CONTRACT_NECESSITY", "OBJECTION_PENDING", "OBJECTION_OVERRIDDEN" -> {
                    // Consent is valid — continue to next attribute
                }
                default -> {
                    // Unknown status — treat as missing consent
                    return Optional.of(new DecisionOutcome(
                            "DENY",
                            "DECISION_CONSENT_REQUIRED",
                            "evidence://decision/consent-required/" + attributeName
                    ));
                }
            }
        }

        // All consent attributes validated successfully
        return Optional.empty();
    }

    /**
     * Extracts the consent status from a consent attribute value.
     * The value may be:
     * <ul>
     *   <li>A simple String status (e.g. {@code "GRANTED"})</li>
     *   <li>A Map with a {@code status} key (e.g. {@code {"status": "WITHDRAWN"}})</li>
     * </ul>
     */
    private String extractStatus(Object attributeValue) {
        if (attributeValue instanceof String s) {
            return s.toUpperCase();
        }
        if (attributeValue instanceof Map<?, ?> m) {
            Object statusObj = m.get("status");
            return statusObj == null ? null : statusObj.toString().toUpperCase();
        }
        return null;
    }
}