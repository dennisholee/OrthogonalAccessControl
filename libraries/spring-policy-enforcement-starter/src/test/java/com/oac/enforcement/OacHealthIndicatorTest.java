package com.oac.enforcement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for {@link OacHealthIndicator}.
 */
@ExtendWith(MockitoExtension.class)
class OacHealthIndicatorTest {

    @Mock
    private DecisionClient decisionClient;

    private OacEntitlementProperties properties;
    private EntitlementRegistry registry;
    private OacHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        properties = new OacEntitlementProperties();
        properties.setContractPaths(List.of("classpath:order-service-api.yaml"));
        properties.setPdpUrl("http://pdp:8080");
        properties.setFailClosed(true);
        properties.getIdentity().setResolverMode("header");

        registry = new EntitlementRegistry(
                properties.getContractPaths(),
                null, // ResourceLoader - won't be called in health()
                properties.isFailClosed()
        );

        healthIndicator = new OacHealthIndicator(registry, decisionClient, properties);
    }

    @Test
    void healthShouldReportUpWithDetails() {
        Health health = healthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertNotNull(health.getDetails());
        assertEquals(0, health.getDetails().get("entitlementsLoaded"));
        assertTrue((Boolean) health.getDetails().get("contractsConfigured"));
        assertEquals("http://pdp:8080", health.getDetails().get("pdpUrl"));
        assertEquals(true, health.getDetails().get("failClosed"));
        assertEquals("header", health.getDetails().get("resolverMode"));
        assertTrue(health.getDetails().get("decisionClientType").toString().contains("Mockito"));
    }

    @Test
    void healthShouldReportDownWhenExceptionOccurs() {
        // Create a registry that throws on size()
        EntitlementRegistry brokenRegistry = new EntitlementRegistry(
                List.of(), null, false) {
            @Override
            public int size() {
                throw new RuntimeException("Test failure");
            }
        };

        OacHealthIndicator broken = new OacHealthIndicator(brokenRegistry, decisionClient, properties);
        Health health = broken.health();

        assertEquals(Status.DOWN, health.getStatus());
    }
}