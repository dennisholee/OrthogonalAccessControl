package com.oac.decision.adapter.out.expression;

import com.oac.decision.application.port.out.ConditionEvaluatorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter that evaluates policy conditions using Spring Expression Language (SpEL).
 *
 * <p>Implements {@link ConditionEvaluatorPort} so the application core
 * remains framework-agnostic. This adapter lives in {@code adapter.out}
 * per clean architecture rules.</p>
 */
@Component
public class SpelConditionEvaluatorAdapter implements ConditionEvaluatorPort {

    private static final Logger log = LoggerFactory.getLogger(SpelConditionEvaluatorAdapter.class);

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ConcurrentHashMap<String, Expression> expressionCache = new ConcurrentHashMap<>();

    private final long maxEvaluationTimeMs;

    public SpelConditionEvaluatorAdapter() {
        this(100);
    }

    public SpelConditionEvaluatorAdapter(long maxEvaluationTimeMs) {
        this.maxEvaluationTimeMs = maxEvaluationTimeMs;
    }

    @Override
    public Optional<Boolean> evaluate(String expression, ConditionEvalContext context) {
        try {
            StandardEvaluationContext evalContext = new StandardEvaluationContext();

            // Put all maps as root-level objects using a wrapper that supports
            // direct property access via SpEL's standard property resolution
            ContextWrapper root = new ContextWrapper(context);
            evalContext.setRootObject(root);

            // Also expose as variables for #variable.key syntax
            evalContext.setVariable("subject", context.subject());
            evalContext.setVariable("resource", context.resource());
            evalContext.setVariable("environment", context.environment());
            evalContext.setVariable("action", context.action());

            Expression expr = expressionCache.computeIfAbsent(expression, parser::parseExpression);

            Boolean result = expr.getValue(evalContext, Boolean.class);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            log.warn("SpEL evaluation failed for expr '{}': {}", expression, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Wrapper that exposes SpEL context data as POJO properties.
     * This enables "subject.department" syntax in SpEL expressions.
     */
    public static class ContextWrapper {
        private final SubjectBean subject;
        private final ResourceBean resource;
        private final EnvironmentBean environment;
        private final String action;
        
        public ContextWrapper(ConditionEvalContext ctx) { 
            this.subject = new SubjectBean(ctx.subject());
            this.resource = new ResourceBean(ctx.resource());
            this.environment = new EnvironmentBean(ctx.environment());
            this.action = ctx.action();
        }
        public SubjectBean getSubject() { return subject; }
        public ResourceBean getResource() { return resource; }
        public EnvironmentBean getEnvironment() { return environment; }
        public String getAction() { return action; }
    }
    
    /** Bean that provides property-style access to a Map */
    public static class SubjectBean {
        private final Map<String, Object> data;
        public SubjectBean(Map<String, Object> data) { this.data = data; }
        public Object get(String key) { return data.get(key); }
        public String getId() { return str("id"); }
        public String getType() { return str("type"); } 
        public String getDepartment() { return str("department"); }
        public String getMarket() { return str("market"); }
        public String getLob() { return str("lob"); }
        public String getGeography() { return str("geography"); }
        public String getChannel() { return str("channel"); }
        public String getTenant() { return str("tenant"); }
        public String getClearance() { return str("clearance"); }
        private String str(String k) { Object v = data.get(k); return v != null ? v.toString() : null; }
    }
    
    /** Bean that provides property-style access to a Map */
    public static class ResourceBean {
        private final Map<String, Object> data;
        public ResourceBean(Map<String, Object> data) { this.data = data; }
        public String getId() { return str("id"); }
        public String getType() { return str("type"); }
        public String getMarket() { return str("market"); }
        public String getLob() { return str("lob"); }
        public String getGeography() { return str("geography"); }
        public String getChannel() { return str("channel"); }
        public String getTenant() { return str("tenant"); }
        public String getRequesterId() { return str("requesterId"); }
        private String str(String k) { Object v = data.get(k); return v != null ? v.toString() : null; }
    }
    
    /** Bean that provides property-style access to a Map */
    public static class EnvironmentBean {
        private final Map<String, Object> data;
        public EnvironmentBean(Map<String, Object> data) { this.data = data; }
        public Object get(String key) { return data.get(key); }
        public Long getHour() { return num("hour"); }
        public Long getRiskScore() { return num("riskScore"); }
        public Long getCurrentHour() { return num("currentHour"); }
        private Long num(String k) { Object v = data.get(k); if (v instanceof Number n) return n.longValue(); if (v != null) try { return Long.parseLong(v.toString()); } catch (Exception e) {} return null; }
    }
}