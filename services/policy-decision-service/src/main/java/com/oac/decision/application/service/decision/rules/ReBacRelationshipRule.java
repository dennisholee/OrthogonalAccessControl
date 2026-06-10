package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.port.out.RelationshipGraphPort;
import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;

import java.util.Optional;

/**
 * Decision rule for ReBAC (Relationship-Based Access Control).
 * Evaluates whether the subject has an active relationship to the target resource
 * via the relationship graph, with bounded BFS traversal up to the configured max depth.
 *
 * This rule sits between BoundaryViolation and Allow in the precedence chain:
 * relationships are evaluated after structural boundary checks but before
 * blanket ALLOW rules.
 */
public class ReBacRelationshipRule implements DecisionRule {

    private final RelationshipGraphPort relationshipGraphPort;

    public ReBacRelationshipRule(RelationshipGraphPort relationshipGraphPort) {
        this.relationshipGraphPort = relationshipGraphPort;
    }

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        // Only evaluate if there are ReBAC-style policies matched
        boolean hasReBACPolicy = context.matchedPolicies().stream()
                .anyMatch(policy -> policy.contains("REBAC") || policy.contains("RELATIONSHIP"));

        if (!hasReBACPolicy) {
            return Optional.empty();
        }

        var request = context.request();
        String subjectId = request.subject().id();
        String resourceId = request.resource().id();
        String resourceType = request.resource().type();

        // Check if a relationship exists (direct or inherited via BFS up to maxDepth)
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

        // Relationship found — allow and delegate to caveat check in AllowRule
        return Optional.of(new DecisionOutcome(
                "ALLOW",
                "DECISION_REBAC_RELATIONSHIP_ALLOW",
                "evidence://decision/rebac/relationship-allow"
        ));
    }
}