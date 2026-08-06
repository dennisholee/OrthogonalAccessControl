package com.oac.decision.adapter.in.web;

import com.oac.decision.adapter.out.consistency.MongoConsistencyTokenAdapter;
import com.oac.decision.application.port.in.DecisionQueryUseCase;
import com.oac.decision.application.port.in.PolicyAdministrationUseCase;
import com.oac.decision.application.port.out.AttributeResolverPort;
import com.oac.decision.application.port.out.AttributeSchemaRegistryPort;
import com.oac.decision.application.port.out.AuditEvidencePort;
import com.oac.decision.application.port.out.ConditionEvaluatorPort;
import com.oac.decision.application.port.out.ConsistencyTokenStore;
import com.oac.decision.application.port.out.ControllerPurposeRegistryPort;
import com.oac.decision.application.port.out.FailOpenEndpointPolicyPort;
import com.oac.decision.application.port.out.ObservabilityPort;
import com.oac.decision.application.port.out.PolicyRegistryPort;
import com.oac.decision.application.port.out.RelationshipGraphPort;
import com.oac.decision.application.service.DecisionApplicationService;
import com.oac.decision.application.service.PolicyAdministrationService;
import com.oac.decision.application.service.decision.CircuitBreaker;
import com.oac.decision.application.service.decision.DecisionCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class DecisionApplicationConfiguration {

    @Bean
    public DecisionQueryUseCase decisionQueryUseCase(
            PolicyRegistryPort policyRegistryPort,
            AttributeResolverPort attributeResolverPort,
            AuditEvidencePort auditEvidencePort,
            ObservabilityPort observabilityPort,
            FailOpenEndpointPolicyPort failOpenEndpointPolicyPort,
            RelationshipGraphPort relationshipGraphPort,
            ConditionEvaluatorPort conditionEvaluatorPort,
            ConsistencyTokenStore consistencyTokenStore,
            CircuitBreaker circuitBreaker,
            DecisionCache decisionCache,
            ControllerPurposeRegistryPort controllerPurposeRegistryPort,
            AttributeSchemaRegistryPort attributeSchemaRegistryPort
    ) {
        return new DecisionApplicationService(
                policyRegistryPort,
                attributeResolverPort,
                auditEvidencePort,
                observabilityPort,
                failOpenEndpointPolicyPort,
                relationshipGraphPort,
                conditionEvaluatorPort,
                consistencyTokenStore,
                circuitBreaker,
                decisionCache,
                controllerPurposeRegistryPort,
                attributeSchemaRegistryPort
        );
    }

    @Bean
    public CircuitBreaker circuitBreaker() {
        return new CircuitBreaker();
    }

    @Bean
    public DecisionCache decisionCache() {
        return new DecisionCache();
    }

    @Bean
    public PolicyAdministrationUseCase policyAdministrationUseCase(
            AuditEvidencePort auditEvidencePort,
            ObservabilityPort observabilityPort,
            AttributeSchemaRegistryPort attributeSchemaRegistryPort
    ) {
        return new PolicyAdministrationService(auditEvidencePort, observabilityPort, attributeSchemaRegistryPort);
    }

    // ============ Phase 2A Adapters ============

    @Bean
    public ConsistencyTokenStore consistencyTokenStore(MongoTemplate mongoTemplate) {
        return new MongoConsistencyTokenAdapter(mongoTemplate);
    }
}