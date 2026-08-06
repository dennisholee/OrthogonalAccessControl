package com.oac.emulator;

import com.oac.decision.adapter.out.attribute.InMemoryAttributeResolverAdapter;
import com.oac.decision.adapter.out.audit.InMemoryAuditEvidenceAdapter;
import com.oac.decision.adapter.out.expression.SpelConditionEvaluatorAdapter;
import com.oac.decision.adapter.out.observability.MetricsObservabilityAdapter;
import com.oac.decision.adapter.out.policy.ClasspathFailOpenEndpointPolicyAdapter;
import com.oac.decision.application.port.out.*;
import com.oac.decision.adapter.out.schema.DefaultAttributeSchemaRegistryStub;
import com.oac.decision.application.service.DecisionApplicationService;
import com.oac.decision.application.service.decision.CircuitBreaker;
import com.oac.decision.application.service.decision.DecisionCache;
import com.oac.decision.application.port.in.DecisionQueryUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Spring configuration that wires the PDP decision engine with in-memory adapters.
 * No MongoDB, no Docker, no Testcontainers — pure Java.
 *
 * <p>Usage:
 * <pre>{@code
 * @Import(StandaloneEmulatorConfiguration.class)
 * // Provides: DecisionQueryUseCase bean (the real PDP rule chain)
 * // Expects: List<Map> beans for "oacPolicies" and "oacRelationships"
 * }</pre>
 */
@Configuration
public class StandaloneEmulatorConfiguration {

    /**
     * Creates the real PDP decision service with in-memory adapters.
     *
     * @param policies         list of policy documents (injected via @Qualifier("oacPolicies"))
     * @param relationships    list of relationship documents (injected via @Qualifier("oacRelationships"))
     */
    @Bean
    public DecisionQueryUseCase decisionQueryUseCase(
            List<Map<String, Object>> policies,
            List<Map<String, Object>> relationships
    ) {
        PolicyRegistryPort policyRegistry = new InMemoryPolicyRegistryAdapter(policies);
        RelationshipGraphPort relationshipGraph = new InMemoryRelationshipGraphAdapter(relationships);
        AttributeResolverPort attributeResolver = new InMemoryAttributeResolverAdapter();
        AuditEvidencePort auditEvidence = new InMemoryAuditEvidenceAdapter();
        ObservabilityPort observability = new MetricsObservabilityAdapter(meterRegistry());
        FailOpenEndpointPolicyPort failOpenPolicy = new ClasspathFailOpenEndpointPolicyAdapter();
        ConditionEvaluatorPort conditionEvaluator = new SpelConditionEvaluatorAdapter();
        ConsistencyTokenStore consistencyTokenStore = new InMemoryConsistencyTokenAdapter();
        ControllerPurposeRegistryPort controllerPurposeRegistry = new InMemoryControllerPurposeRegistryAdapter();

        return new DecisionApplicationService(
                policyRegistry,
                attributeResolver,
                auditEvidence,
                observability,
                failOpenPolicy,
                relationshipGraph,
                conditionEvaluator,
                consistencyTokenStore,
                new CircuitBreaker(),
                new DecisionCache(),
                controllerPurposeRegistry,
                new DefaultAttributeSchemaRegistryStub()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    /**
     * In-memory consistency token store — no MongoDB required.
     */
    private static class InMemoryConsistencyTokenAdapter implements ConsistencyTokenStore {
        private String latestToken;
        private long sequence = 0;

        @Override
        public java.util.Optional<String> getLatestToken(String scope) {
            return latestToken != null ? java.util.Optional.of(latestToken) : java.util.Optional.empty();
        }

        @Override
        public String issueToken(String scope) {
            long now = java.time.Instant.now().getEpochSecond();
            latestToken = scope + "-" + now + "-" + (++sequence);
            return latestToken;
        }
    }
}