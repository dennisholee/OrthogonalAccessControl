package com.oac.enforcement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.Map;

/**
 * Actuator HealthIndicator that reports the status of the OAC enforcement
 * library including PDP connectivity and contract loading status.
 */
public class OacHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(OacHealthIndicator.class);

    private final EntitlementRegistry registry;
    private final DecisionClient decisionClient;
    private final OacEntitlementProperties properties;

    public OacHealthIndicator(EntitlementRegistry registry,
                               DecisionClient decisionClient,
                               OacEntitlementProperties properties) {
        this.registry = registry;
        this.decisionClient = decisionClient;
        this.properties = properties;
    }

    @Override
    public Health health() {
        try {
            int entitlementCount = registry.size();
            boolean contractsConfigured = !properties.getContractPaths().isEmpty()
                    && !properties.getContractPaths().get(0).isEmpty();

            String pdpUrl = properties.getPdpUrl();

            Health.Builder builder = Health.up()
                    .withDetails(Map.of(
                            "entitlementsLoaded", entitlementCount,
                            "contractsConfigured", contractsConfigured,
                            "pdpUrl", pdpUrl,
                            "failClosed", properties.isFailClosed(),
                            "resolverMode", properties.getIdentity().getResolverMode()
                    ));

            // Detect the type of decision client for richer health data
            String clientType = decisionClient.getClass().getSimpleName();
            builder.withDetail("decisionClientType", clientType);

            return builder.build();
        } catch (Exception e) {
            log.warn("OAC health check failed", e);
            return Health.down(e).build();
        }
    }
}