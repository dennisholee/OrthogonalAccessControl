package com.oac.sample.bdd;

import com.oac.decision.adapter.in.web.DecisionApplicationConfiguration;
import com.oac.decision.adapter.out.attribute.InMemoryAttributeResolverAdapter;
import com.oac.decision.adapter.out.audit.InMemoryAuditEvidenceAdapter;
import com.oac.decision.adapter.out.expression.SpelConditionEvaluatorAdapter;
import com.oac.decision.adapter.out.observability.MetricsObservabilityAdapter;
import com.oac.decision.adapter.out.policy.ClasspathFailOpenEndpointPolicyAdapter;
import com.oac.decision.adapter.out.policy.MongoPolicyRegistryAdapter;
import com.oac.decision.adapter.out.purpose.MongoControllerPurposeRegistryAdapter;
import com.oac.decision.adapter.out.relationship.MongoRelationshipGraphAdapter;
import com.oac.decision.adapter.out.schema.MongoAttributeSchemaRegistryAdapter;
import com.oac.decision.application.port.in.DecisionQueryUseCase;
import com.oac.enforcement.DecisionClient;
import io.cucumber.spring.CucumberContextConfiguration;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;

/**
 * Cucumber Spring configuration that wires up:
 * 1. Testcontainers MongoDB (started statically before Spring context loads)
 * 2. The sample order service (with its own Orders in MongoDB)
 * 3. The PDP rule engine in-process (with Policies/Relationships in MongoDB)
 * 4. A DirectDecisionClient bridging the sample service to the PDP rule engine
 *
 * Both the sample service and PDP share the same MongoDB instance
 * (separate collections: "orders" for sample service, "policies"/"relationships" for PDP).
 */
@CucumberContextConfiguration
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.data.mongodb.auto-index-creation=true",
            "oac.pdp.url=",
            "spring.main.allow-bean-definition-overriding=true"
        }
)
@ActiveProfiles("mongodb")
@Import({
        DecisionApplicationConfiguration.class,
        CucumberSpringConfiguration.TestPdpPorts.class
})
public class CucumberSpringConfiguration {

    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    static {
        mongoDBContainer.start();
    }

    @Value("${local.server.port}")
    private int instancePort;

    private static int port;

    @PostConstruct
    void init() {
        port = instancePort;
    }

    public static int getPort() {
        return port;
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @TestConfiguration
    @Profile("mongodb")
    static class TestPdpPorts {

        @Bean
        public org.springframework.web.client.RestTemplate restTemplate() {
            return new org.springframework.web.client.RestTemplate();
        }
        

        /**
         * In-process DecisionClient that bridges the sample service
         * to the PDP's rule engine without an HTTP call.
         */
        @Bean
        @Primary
        public DecisionClient directDecisionClient(DecisionQueryUseCase decisionQueryUseCase) {
            return new DirectDecisionClient(decisionQueryUseCase);
        }

        @Bean
        public com.oac.decision.application.port.out.PolicyRegistryPort policyRegistryPort(
                MongoTemplate mongoTemplate) {
            return new MongoPolicyRegistryAdapter(mongoTemplate);
        }

        @Bean
        public com.oac.decision.application.port.out.ControllerPurposeRegistryPort controllerPurposeRegistryPort(
                MongoTemplate mongoTemplate) {
            return new MongoControllerPurposeRegistryAdapter(mongoTemplate);
        }

        @Bean
        public com.oac.decision.application.port.out.AttributeSchemaRegistryPort attributeSchemaRegistryPort(
                MongoTemplate mongoTemplate) {
            return new MongoAttributeSchemaRegistryAdapter(mongoTemplate);
        }

        @Bean
        public com.oac.decision.application.port.out.RelationshipGraphPort relationshipGraphPort(
                MongoTemplate mongoTemplate) {
            return new MongoRelationshipGraphAdapter(mongoTemplate);
        }

        @Bean
        public com.oac.decision.application.port.out.AttributeResolverPort attributeResolverPort() {
            return new InMemoryAttributeResolverAdapter();
        }

        @Bean
        public com.oac.decision.application.port.out.AuditEvidencePort auditEvidencePort() {
            return new InMemoryAuditEvidenceAdapter();
        }

        @Bean
        public com.oac.decision.application.port.out.ObservabilityPort observabilityPort(
                io.micrometer.core.instrument.MeterRegistry meterRegistry) {
            return new MetricsObservabilityAdapter(meterRegistry);
        }

        @Bean
        public com.oac.decision.application.port.out.FailOpenEndpointPolicyPort failOpenEndpointPolicyPort() {
            return new ClasspathFailOpenEndpointPolicyAdapter();
        }

        @Bean
        public com.oac.decision.application.port.out.ConditionEvaluatorPort conditionEvaluatorPort() {
            return new SpelConditionEvaluatorAdapter();
        }
    }
}