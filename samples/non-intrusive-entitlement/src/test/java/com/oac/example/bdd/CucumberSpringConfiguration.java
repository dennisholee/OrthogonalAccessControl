package com.oac.example.bdd;

import com.oac.decision.adapter.in.web.DecisionApplicationConfiguration;
import com.oac.decision.adapter.out.attribute.InMemoryAttributeResolverAdapter;
import com.oac.decision.adapter.out.audit.InMemoryAuditEvidenceAdapter;
import com.oac.decision.adapter.out.observability.MetricsObservabilityAdapter;
import com.oac.decision.adapter.out.policy.ClasspathFailOpenEndpointPolicyAdapter;
import com.oac.decision.adapter.out.policy.MongoPolicyRegistryAdapter;
import com.oac.decision.adapter.out.relationship.MongoRelationshipGraphAdapter;
import com.oac.decision.application.port.in.DecisionQueryUseCase;
import com.oac.decision.application.port.out.*;
import com.oac.enforcement.DecisionClient;
import com.oac.enforcement.resolver.SubjectResolverDelegate;
import io.cucumber.spring.CucumberContextConfiguration;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;

/**
 * Cucumber Spring configuration that wires up:
 * 1. Testcontainers MongoDB (started statically before Spring context loads)
 * 2. The non-intrusive-entitlement sample service (in-memory orders)
 * 3. The PDP rule engine in-process (Policies/Relationships in MongoDB)
 * 4. A DirectDecisionClient bridging the sample service to the PDP rule engine
 * 5. Composite subject resolver with JWT and custom delegate support for BDD tests
 */
@CucumberContextConfiguration
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.data.mongodb.auto-index-creation=true",
            "oac.pdp.url=",
            "spring.main.allow-bean-definition-overriding=true",
            "oac.enforcement.contract-paths[0]=classpath:order-service-api.yaml",
            "oac.enforcement.identity.resolver-mode=composite",
            "oac.enforcement.identity.jwt.schemas.default.claim=sub",
            "oac.enforcement.identity.jwt.schemas.oidc.claim=preferred_username"
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
        registry.add("oac.enforcement.identity.jwt.schemas.default.secret",
                () -> JwtTokenFactory.getDefaultBase64Secret());
        registry.add("oac.enforcement.identity.jwt.schemas.oidc.secret",
                () -> JwtTokenFactory.getDefaultBase64Secret());
        // workload schema used when subjectType=workload (getAggregate endpoint)
        registry.add("oac.enforcement.identity.jwt.schemas.workload.claim",
                () -> "preferred_username");
        registry.add("oac.enforcement.identity.jwt.schemas.workload.secret",
                () -> JwtTokenFactory.getDefaultBase64Secret());
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
        public PolicyRegistryPort policyRegistryPort(MongoTemplate mongoTemplate) {
            return new MongoPolicyRegistryAdapter(mongoTemplate);
        }

        @Bean
        public RelationshipGraphPort relationshipGraphPort(MongoTemplate mongoTemplate) {
            return new MongoRelationshipGraphAdapter(mongoTemplate);
        }

        @Bean
        public AttributeResolverPort attributeResolverPort() {
            return new InMemoryAttributeResolverAdapter();
        }

        @Bean
        public AuditEvidencePort auditEvidencePort() {
            return new InMemoryAuditEvidenceAdapter();
        }

        @Bean
        public ObservabilityPort observabilityPort(io.micrometer.core.instrument.MeterRegistry meterRegistry) {
            return new MetricsObservabilityAdapter(meterRegistry);
        }

        @Bean
        public FailOpenEndpointPolicyPort failOpenEndpointPolicyPort() {
            return new ClasspathFailOpenEndpointPolicyAdapter();
        }

        @Bean
        public JwtTokenFactory jwtTokenFactory() {
            return JwtTokenFactory.defaultFactory();
        }

        /**
         * A custom SubjectResolverDelegate that extracts subject from X-Custom-Id header.
         * Order(20) ensures it runs after Jwt (Order 10) and Header (Order 0) resolvers.
         */
        @Bean
        @Order(20)
        public SubjectResolverDelegate customSubjectResolverDelegate() {
            return (request, config) -> request.getHeader("X-Custom-Id");
        }
    }
}