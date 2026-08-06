package com.oac.decision.adapter.out.expression;

import com.oac.decision.application.port.out.ConditionEvaluatorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.expression.spel.support.ReflectiveMethodResolver;
import org.springframework.context.expression.MapAccessor;
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
 *
 * <p>Expression sandboxing (docs/POLICY_ARCHITECTURE.md Section 4.1): this adapter restricts
 * {@link StandardEvaluationContext} to explicitly block {@code T(...)} type references,
 * constructor invocations, static method access, and bean references ({@code @bean}).
 * Property access is whitelisted to the context beans plus {@link MapAccessor} for
 * {@code map.key} style access. Only instance methods on exposed beans are callable.</p>
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
            ContextWrapper root = new ContextWrapper(context);

            // Restricted StandardEvaluationContext:
            // - Property access: ReflectivePropertyAccessor (POJO getters) + MapAccessor (map.key)
            // - Method resolution: ReflectiveMethodResolver (instance methods like List.contains)
            // - Type references T(...): BLOCKED via TypeLocator that throws for every type
            // - Constructor invocations: BLOCKED (empty constructor resolver list)
            // - Static method access: BLOCKED (type references already blocked)
            // - Bean references @bean: BLOCKED (no BeanResolver registered)
            StandardEvaluationContext evalContext = new StandardEvaluationContext();
            evalContext.setPropertyAccessors(java.util.List.of(
                    new ReflectivePropertyAccessor(), new MapAccessor()));
            evalContext.setMethodResolvers(java.util.List.of(new ReflectiveMethodResolver()));
            evalContext.setConstructorResolvers(java.util.List.of());
            evalContext.setTypeLocator(typeName -> {
                throw new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, typeName);
            });
            evalContext.setRootObject(root);

            // Expose bean instances as variables for #subject.property syntax.
            // Beans are used instead of raw Maps so property-style access works via getters.
            evalContext.setVariable("subject", root.getSubject());
            evalContext.setVariable("resource", root.getResource());
            evalContext.setVariable("environment", root.getEnvironment());
            evalContext.setVariable("action", context.action());
            evalContext.setVariable("principalMemberships", root.getPrincipalMemberships());

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
        private final PrincipalMembershipsBean principalMemberships;
        
        public ContextWrapper(ConditionEvalContext ctx) { 
            this.subject = new SubjectBean(ctx.subject());
            this.resource = new ResourceBean(ctx.resource());
            this.environment = new EnvironmentBean(ctx.environment());
            this.action = ctx.action();
            this.principalMemberships = new PrincipalMembershipsBean(ctx.principalMemberships());
        }
        public SubjectBean getSubject() { return subject; }
        public ResourceBean getResource() { return resource; }
        public EnvironmentBean getEnvironment() { return environment; }
        public String getAction() { return action; }
        public PrincipalMembershipsBean getPrincipalMemberships() { return principalMemberships; }
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
        public Object get(String key) { return data.get(key); }
        public String getId() { return str("id"); }
        public String getType() { return str("type"); }
        public String getMarket() { return str("market"); }
        public String getLob() { return str("lob"); }
        public String getGeography() { return str("geography"); }
        public String getChannel() { return str("channel"); }
        public String getTenant() { return str("tenant"); }
        public String getRequesterId() { return str("requesterId"); }
        // CDP resource attributes (docs/POLICY_ARCHITECTURE.md Section 3.5)
        public String getRegulatoryRegime() { return str("regulatoryRegime"); }
        public Object getDataSources() { return data.get("dataSources"); }
        public String getDataSubjectCategory() { return str("dataSubjectCategory"); }
        public Object getSuppressionFlags() { return data.get("suppressionFlags"); }
        public Object getConsentAttributes() { return data.get("consentAttributes"); }
        public Object getConsentVersion() { return data.get("consentVersion"); }
        public Object getSegments() { return data.get("segments"); }
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
        public String getPurpose() { return str("purpose"); }
        public String getExportDestination() { return str("exportDestination"); }
        public String getDeploymentEnvironment() { return str("deploymentEnvironment"); }
        private String str(String k) { Object v = data.get(k); return v != null ? v.toString() : null; }
        private Long num(String k) { Object v = data.get(k); if (v instanceof Number n) return n.longValue(); if (v != null) try { return Long.parseLong(v.toString()); } catch (Exception e) {} return null; }
    }

    /**
     * Principal domain membership accessor (docs/POLICY_ARCHITECTURE.md Section 4.33).
     * Exposes the seven membership lists for SpEL expressions like
     * {@code #principalMemberships.tenants.contains('tenant-a')}.
     * When memberships are absent, all lists default to empty (no NPE).
     */
    public static class PrincipalMembershipsBean {
        private final Map<String, Object> data;

        @SuppressWarnings("unchecked")
        public PrincipalMembershipsBean(Map<String, Object> data) {
            this.data = data == null ? Map.of() : data;
        }

        public Object get(String key) { return data.get(key); }
        public java.util.List<String> getTenants() { return list("tenants"); }
        public java.util.List<String> getGeographies() { return list("geographies"); }
        public java.util.List<String> getMarkets() { return list("markets"); }
        public java.util.List<String> getLinesOfBusiness() { return list("linesOfBusiness"); }
        public java.util.List<String> getChannels() { return list("channels"); }
        public java.util.List<String> getPurposes() { return list("purposes"); }
        public java.util.List<String> getRegulatoryRegimes() { return list("regulatoryRegimes"); }

        @SuppressWarnings("unchecked")
        private java.util.List<String> list(String key) {
            Object v = data.get(key);
            if (v instanceof java.util.List<?> l) return (java.util.List<String>) l;
            return java.util.List.of();
        }
    }
}