package com.oac.decision.bdd.probe;

import com.oac.decision.application.port.out.DecisionTracePort;
import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Thread-local in-memory decision trace probe for BDD test assertions.
 *
 * <p>Each Cucumber scenario runs in a dedicated thread. This probe collects
 * {@link DecisionTrace} snapshots which BDD step definitions can assert on
 * after each decision call:
 *
 * <pre>{@code
 * @Then("the rule {string} should have evaluated")
 * public void verifyRuleEvaluated(String ruleName) {
 *     var trace = decisionTraceProbe.lastTrace();
 *     assertThat(trace.ruleEvaluations())
 *         .anyMatch(e -> e.ruleName().equals(ruleName));
 * }
 * }</pre>
 */
@Component
@Profile("mongodb")
public class InMemoryDecisionTraceProbe implements DecisionTracePort {

    private final ThreadLocal<Builder> currentBuilder = ThreadLocal.withInitial(Builder::new);
    private final ThreadLocal<Deque<DecisionTrace>> traceStore = ThreadLocal.withInitial(ConcurrentLinkedDeque::new);

    private Builder currentBuilder() {
        return currentBuilder.get();
    }

    @Override
    public void traceRuleEvaluation(String ruleName, DecisionContext ctx, Optional<DecisionOutcome> outcome) {
        currentBuilder().addRuleEvaluation(new RuleEvaluation(
                ruleName,
                outcome.map(DecisionOutcome::decision),
                outcome.map(DecisionOutcome::decisionCode),
                outcome.map(DecisionOutcome::evidenceRef),
                outcome.map(o -> o.diagnostics() == null ? Map.<String, Object>of() : o.diagnostics())
        ));
    }

    @Override
    public void tracePolicyMatches(List<String> matchedPolicies, List<Map<String, Object>> rawDocuments) {
        currentBuilder().matchedPolicies(matchedPolicies);
    }

    @Override
    public void traceResolvedContext(Map<String, Object> resolvedContext) {
        currentBuilder().resolvedContext(resolvedContext);
    }

    @Override
    public Map<String, Object> diagnosticPayload() {
        var trace = lastTrace();
        if (trace == null) return null;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subjectId", trace.subjectId());
        payload.put("action", trace.action());
        payload.put("resource", trace.resourceType() + "/" + trace.resourceId());
        payload.put("hasAllow", trace.hasAllow());
        payload.put("matchedPolicies", trace.matchedPolicies());
        payload.put("ruleEvaluations", trace.ruleEvaluations().stream()
                .map(e -> Map.of(
                        "rule", e.ruleName(),
                        "decision", e.decision().orElse("skip"),
                        "code", e.decisionCode().orElse("N/A"),
                        "evidence", e.evidenceRef().orElse("N/A")
                ))
                .toList());
        payload.put("finalDecision", trace.finalOutcome() != null
                ? trace.finalOutcome().decision() : "N/A");
        payload.put("finalCode", trace.finalOutcome() != null
                ? trace.finalOutcome().decisionCode() : "N/A");
        return payload;
    }

    /**
     * Return the most recently completed trace, or {@code null} if no
     * decision has been evaluated in this thread.
     */
    public DecisionTrace lastTrace() {
        var deque = traceStore.get();
        return deque.isEmpty() ? null : deque.peekFirst();
    }

    /** Called by the BDD steps to finalize and store a completed trace. */
    public void completeTrace(DecisionOutcome finalOutcome) {
        var builder = currentBuilder.get();
        var trace = builder
                .finalOutcome(finalOutcome)
                .build();
        currentBuilder.remove();
        var deque = traceStore.get();
        deque.clear();
        deque.addFirst(trace);
    }

    /** Reset all traces for the current thread (called in @Before). */
    public void reset() {
        currentBuilder.remove();
        traceStore.get().clear();
    }

    // ==================== Builder ====================

    public static final class DecisionTrace {
        private final String subjectId;
        private final String action;
        private final String resourceType;
        private final String resourceId;
        private final boolean hasAllow;
        private final List<String> matchedPolicies;
        private final Map<String, Object> resolvedContext;
        private final List<RuleEvaluation> ruleEvaluations;
        private final DecisionOutcome finalOutcome;

        private DecisionTrace(Builder b) {
            this.subjectId = b.subjectId;
            this.action = b.action;
            this.resourceType = b.resourceType;
            this.resourceId = b.resourceId;
            this.hasAllow = b.hasAllow;
            this.matchedPolicies = b.matchedPolicies == null ? List.of() : List.copyOf(b.matchedPolicies);
            this.resolvedContext = b.resolvedContext == null ? Map.of() : Map.copyOf(b.resolvedContext);
            this.ruleEvaluations = b.ruleEvaluations == null ? List.of() : List.copyOf(b.ruleEvaluations);
            this.finalOutcome = b.finalOutcome;
        }

        public String subjectId() { return subjectId; }
        public String action() { return action; }
        public String resourceType() { return resourceType; }
        public String resourceId() { return resourceId; }
        public boolean hasAllow() { return hasAllow; }
        public List<String> matchedPolicies() { return matchedPolicies; }
        public Map<String, Object> resolvedContext() { return resolvedContext; }
        public List<RuleEvaluation> ruleEvaluations() { return ruleEvaluations; }
        public DecisionOutcome finalOutcome() { return finalOutcome; }
    }

    public static final class Builder {
        private String subjectId;
        private String action;
        private String resourceType;
        private String resourceId;
        private boolean hasAllow;
        private List<String> matchedPolicies;
        private Map<String, Object> resolvedContext;
        private List<RuleEvaluation> ruleEvaluations = new ArrayList<>();
        private DecisionOutcome finalOutcome;

        private Builder() {}

        public Builder subjectId(String v) { this.subjectId = v; return this; }
        public Builder action(String v) { this.action = v; return this; }
        public Builder resourceType(String v) { this.resourceType = v; return this; }
        public Builder resourceId(String v) { this.resourceId = v; return this; }
        public Builder hasAllow(boolean v) { this.hasAllow = v; return this; }
        public Builder matchedPolicies(List<String> v) { this.matchedPolicies = v; return this; }
        public Builder resolvedContext(Map<String, Object> v) { this.resolvedContext = v; return this; }
        public Builder addRuleEvaluation(RuleEvaluation e) { this.ruleEvaluations.add(e); return this; }
        public Builder finalOutcome(DecisionOutcome v) { this.finalOutcome = v; return this; }

        public DecisionTrace build() { return new DecisionTrace(this); }
    }
}