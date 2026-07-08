package com.oac.decision.application.port.out;

import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port for tracing decision evaluation at every rule checkpoint.
 *
 * <p>In production, implementations log structured traces to observability backends.
 * In test mode, implementations capture traces for assertion by BDD step definitions.
 *
 * <p>Each top-level {@code checkPermission} evaluation produces a {@code DecisionTrace}
 * containing the full rule chain, resolved context, and policy matches.
 */
public interface DecisionTracePort {

    /**
     * Record that a {@link com.oac.decision.application.service.decision.DecisionRule}
     * was evaluated. Called after each rule's {@code evaluate()} returns.
     *
     * @param ruleName the simple class name of the rule (e.g. "BoundaryViolationRule")
     * @param ctx      the decision context at evaluation time
     * @param outcome  the rule's outcome, or empty if the rule declined
     */
    void traceRuleEvaluation(String ruleName, DecisionContext ctx, Optional<DecisionOutcome> outcome);

    /**
     * Record which policies were matched by the registry query, along with
     * the raw MongoDB document that produced each match.
     *
     * @param matchedPolicies list of matched policy strings (e.g. "POL.ALLOW.POL.E2E.ACCOUNT.BASELINE.ALLOW.v1")
     * @param rawDocuments    the corresponding raw MongoDB documents (for diagnostic inspection)
     */
    void tracePolicyMatches(List<String> matchedPolicies, List<Map<String, Object>> rawDocuments);

    /**
     * Record the resolved runtime context after all attribute resolution is complete.
     */
    void traceResolvedContext(Map<String, Object> resolvedContext);

    /**
     * Return a diagnostic payload suitable for inclusion in the check-permission response.
     * Implementations may return null in production mode.
     */
    Map<String, Object> diagnosticPayload();

    /**
     * Complete snapshot of a single decision evaluation.
     */
    record DecisionTrace(
            String requestId,
            String subjectId,
            String action,
            String resourceType,
            String resourceId,
            String tenant,
            boolean hasAllow,
            List<String> matchedPolicies,
            Map<String, Object> resolvedContext,
            List<RuleEvaluation> ruleEvaluations,
            DecisionOutcome finalOutcome
    ) {}

    record RuleEvaluation(
            String ruleName,
            Optional<String> decision,
            Optional<String> decisionCode,
            Optional<String> evidenceRef,
            Optional<Map<String, Object>> diagnostics
    ) {}
}