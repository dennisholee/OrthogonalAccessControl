package com.oac.decision.adapter.out.expression;

import com.oac.decision.application.port.out.ConditionEvaluatorPort;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sandboxing contract test — verifies the SpEL evaluator blocks dangerous constructs
 * (type references, constructor calls, static method access, bean references) and
 * still allows legitimate property access.
 *
 * <p>Per docs/POLICY_ARCHITECTURE.md Section 4.1, the evaluator MUST use
 * {@code SimpleEvaluationContext} instead of {@code StandardEvaluationContext}.
 * The blocking of {@code T(...)} type references is the key security guarantee.</p>
 */
class SpelSandboxingContractTest {

    private final SpelConditionEvaluatorAdapter evaluator = new SpelConditionEvaluatorAdapter();

    private static ConditionEvaluatorPort.ConditionEvalContext ctx() {
        return new ConditionEvaluatorPort.ConditionEvalContext(
                Map.of("id", "u-1", "type", "human", "department", "compliance"),
                Map.of("id", "acc-1", "type", "account"),
                Map.of("currentHour", 10L),
                "read"
        );
    }

    @Test
    void typeReferenceToRuntimeIsBlocked() {
        Optional<Boolean> result = evaluator.evaluate(
                "T(java.lang.Runtime).getRuntime().exec('echo pwned')", ctx());
        assertThat(result).isEmpty(); // must not evaluate to a value
    }

    @Test
    void typeReferenceToThreadSleepIsBlocked() {
        long start = System.currentTimeMillis();
        Optional<Boolean> result = evaluator.evaluate("T(Thread).sleep(10000)", ctx());
        long elapsed = System.currentTimeMillis() - start;
        assertThat(result).isEmpty();
        // If the sleep actually executed, elapsed would be ~10s. Blocked = fast.
        assertThat(elapsed).isLessThan(3000);
    }

    @Test
    void constructorInvocationIsBlocked() {
        Optional<Boolean> result = evaluator.evaluate("new java.io.File('/tmp/exploit')", ctx());
        assertThat(result).isEmpty();
    }

    @Test
    void beanReferenceIsBlocked() {
        Optional<Boolean> result = evaluator.evaluate("@anyBean.execute()", ctx());
        assertThat(result).isEmpty();
    }

    @Test
    void staticMethodInvocationIsBlocked() {
        Optional<Boolean> result = evaluator.evaluate(
                "T(System).getProperty('java.home')", ctx());
        assertThat(result).isEmpty();
    }

    @Test
    void legitimatePropertyAccessStillWorks() {
        Optional<Boolean> result = evaluator.evaluate(
                "subject.department == 'compliance'", ctx());
        assertThat(result).hasValue(true);
    }

    @Test
    void legitimateVariableReferenceWithHashStillWorks() {
        Optional<Boolean> result = evaluator.evaluate(
                "#subject.department == 'compliance'", ctx());
        assertThat(result).hasValue(true);
    }
}
