package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.port.out.RelationshipGraphPort;
import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;

import java.util.Map;
import java.util.Optional;

/**
 * Decision rule for ReBAC (Relationship-Based Access Control).
 */
public class ReBacRelationshipRule implements DecisionRule {

    private final RelationshipGraphPort relationshipGraphPort;

    public ReBacRelationshipRule(RelationshipGraphPort relationshipGraphPort) {
        this.relationshipGraphPort = relationshipGraphPort;
    }

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        boolean hasReBACPolicy = context.matchedPolicies().stream()
                .anyMatch(policy -> (policy.contains("REBAC") || policy.contains("RELATIONSHIP"))
                        && !policy.contains("FIELD"));

        if (!hasReBACPolicy) {
            return Optional.empty();
        }

        var request = context.request();
        String subjectId = request.subject().id();
        String resourceId = request.resource().id();

        // Extract relationship type and boundary scope from matched policies.
        // Format: POL.ALLOW.<name>:REBAC.<type>:SCOPE.market=retail,lob=cards
        String relationshipType = null;
        Map<String, String> relationshipBoundaryScope = new java.util.LinkedHashMap<>();
        for (String policy : context.matchedPolicies()) {
            if (!policy.contains("REBAC") && !policy.contains("RELATIONSHIP")) continue;
            int idx = policy.lastIndexOf(":REBAC.");
            if (idx == -1) idx = policy.lastIndexOf(":RELATIONSHIP.");
            if (idx >= 0) {
                String suffix = policy.substring(idx + (policy.charAt(idx + 1) == 'R' ? 7 : 13));
                int nc = suffix.indexOf(':');
                if (nc > 0) suffix = suffix.substring(0, nc);
                String extracted = suffix.trim();
                if (!extracted.isEmpty()) {
                    relationshipType = extracted;
                    break;
                }
            }
        }
        // Extract :SCOPE.<k>=<v>,<k>=<v> segment from the matched policy entry.
        for (String policy : context.matchedPolicies()) {
            int scopeIdx = policy.lastIndexOf(":SCOPE.");
            if (scopeIdx >= 0) {
                String scopePart = policy.substring(scopeIdx + ":SCOPE.".length());
                int end = scopePart.indexOf(':');
                if (end > 0) scopePart = scopePart.substring(0, end);
                for (String pair : scopePart.split(",")) {
                    int eq = pair.indexOf('=');
                    if (eq > 0) {
                        relationshipBoundaryScope.put(
                                pair.substring(0, eq).trim(),
                                pair.substring(eq + 1).trim());
                    }
                }
            }
        }

        // Use null for type to traverse through intermediate hops of any type.
        // Then verify the final edge type matches if required.
        boolean hasRelationship = relationshipGraphPort.hasRelationship(
                subjectId, resourceId, null,
                relationshipGraphPort.getMaxTraversalDepth()
        );

        if (!hasRelationship) {
            return Optional.of(new DecisionOutcome(
                    "DENY",
                    "DECISION_REBAC_NO_RELATIONSHIP",
                    "evidence://decision/rebac/no-relationship"
            ));
        }

        // If a specific type is required, do a second check with the type.
        // This ensures that when policy requires "manages", an "approver"
        // relationship alone doesn't grant access.
        if (relationshipType != null) {
            hasRelationship = relationshipGraphPort.hasRelationship(
                    subjectId, resourceId, relationshipType,
                    relationshipGraphPort.getMaxTraversalDepth()
            );
            if (!hasRelationship) {
                return Optional.of(new DecisionOutcome(
                        "DENY",
                        "DECISION_REBAC_NO_RELATIONSHIP",
                        "evidence://decision/rebac/no-relationship-type"
                ));
            }
        }

        // If the policy declares a relationshipBoundaryScope (Section 4.36 composable domains),
        // verify that a scoped path exists — the unscoped traversal above is not sufficient.
        if (!relationshipBoundaryScope.isEmpty()) {
            hasRelationship = relationshipGraphPort.hasRelationship(
                    subjectId, resourceId, relationshipType,
                    relationshipGraphPort.getMaxTraversalDepth(),
                    relationshipBoundaryScope
            );
            if (!hasRelationship) {
                return Optional.of(new DecisionOutcome(
                        "DENY",
                        "DECISION_REBAC_NO_RELATIONSHIP",
                        "evidence://decision/rebac/no-relationship-scope"
                ));
            }
        }

        return Optional.empty();
    }
}