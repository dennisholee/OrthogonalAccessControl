package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.port.out.ConditionEvaluatorPort;
import com.oac.decision.application.port.shared.AttributeReferenceExtractor;
import com.oac.decision.application.service.decision.ConditionEvalContextBuilder;
import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;

import java.util.Optional;

/**
 * Evaluates SpEL conditions on policies via the {@link ConditionEvaluatorPort}.
 */
public class SpelConditionRule implements DecisionRule {

    private final ConditionEvaluatorPort conditionEvaluator;

    public SpelConditionRule(ConditionEvaluatorPort conditionEvaluator) {
        this.conditionEvaluator = conditionEvaluator;
    }

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        for (String policy : context.matchedPolicies()) {
            String conditionExpr = AttributeReferenceExtractor.extractSpelExpression(policy);
            if (conditionExpr == null || conditionExpr.isBlank()) {
                continue;
            }

            ConditionEvaluatorPort.ConditionEvalContext evalContext = ConditionEvalContextBuilder.build(context);

            Optional<Boolean> result = conditionEvaluator.evaluate(conditionExpr, evalContext);

            if (result.isEmpty() || !result.get()) {
                String code = result.isEmpty() ? "SPEL_EVALUATION_ERROR" : "SPEL_CONDITION_FAILED";
                return Optional.of(new DecisionOutcome(
                        "DENY",
                        code,
                        "evidence://decision/spel/" + Integer.toHexString(conditionExpr.hashCode())
                ));
            }
        }

        return Optional.empty();
    }
}