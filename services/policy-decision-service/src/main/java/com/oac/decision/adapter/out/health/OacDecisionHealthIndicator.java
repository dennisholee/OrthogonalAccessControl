package com.oac.decision.adapter.out.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Decision-service health indicator surfaced under the {@code oacDecision}
 * actuator component.
 * <p>
 * Reports {@code DEGRADED} when the policy registry dependency is unavailable
 * (toggled by the MongoDB outage simulation path) and {@code UP} otherwise.
 */
@Component("oacDecision")
public class OacDecisionHealthIndicator implements HealthIndicator {

    private volatile boolean degraded = false;

    public void setDegraded(boolean degraded) {
        this.degraded = degraded;
    }

    public boolean isDegraded() {
        return degraded;
    }

    @Override
    public Health health() {
        if (degraded) {
            return Health.status("DEGRADED").withDetail("mongo", "unavailable").build();
        }
        return Health.up().build();
    }
}
