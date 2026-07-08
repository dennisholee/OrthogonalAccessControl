package com.oac.decision.application.port.out;

import com.oac.decision.application.service.decision.DecisionContext;

import java.util.Map;
import java.util.Optional;

/**
 * Output port for evaluating policy conditions.
 *
 * <p>This isolates SpEL (or other expression language) evaluation
 * behind a port, keeping the application core framework-agnostic.</p>
 */
public interface ConditionEvaluatorPort {

    /**
     * Evaluate a condition expression against the decision context.
     *
     * @param expression the expression string (e.g., SpEL)
     * @param context    the decision evaluation context
     * @return true if the condition passes, false if it fails, empty if not applicable
     */
    Optional<Boolean> evaluate(String expression, ConditionEvalContext context);

    /**
     * Data carrier for condition evaluation variables.
     */
    record ConditionEvalContext(
            Map<String, Object> subject,
            Map<String, Object> resource,
            Map<String, Object> environment,
            String action
    ) {}
}