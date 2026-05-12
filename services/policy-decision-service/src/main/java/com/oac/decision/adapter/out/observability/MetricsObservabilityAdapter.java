package com.oac.decision.adapter.out.observability;

import com.oac.decision.application.port.out.ObservabilityPort;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MetricsObservabilityAdapter implements ObservabilityPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsObservabilityAdapter.class);

    private final MeterRegistry meterRegistry;

    public MetricsObservabilityAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordDecision(String decisionCode) {
        meterRegistry.counter("oac.decision.evaluations", "decisionCode", decisionCode).increment();
    }

    @Override
    public void recordPolicyLifecycleTransition(String fromState, String toState) {
        meterRegistry.counter("oac.policy.lifecycle.transitions", "from", fromState, "to", toState).increment();
    }

    @Override
    public void recordSecurityAlert(String alertType) {
        meterRegistry.counter("oac.security.alerts", "type", alertType).increment();
        LOGGER.warn("High-signal security alert raised: {}", alertType);
    }

    @Override
    public void recordRegionalLag(String operation, long lagMs) {
        DistributionSummary.builder("oac.consistency.regional.lag.ms")
                .tag("operation", operation)
                .register(meterRegistry)
                .record(Math.max(lagMs, 0));
    }

    @Override
    public void recordReplicaVersionGap(String operation, long versionGap) {
        DistributionSummary.builder("oac.consistency.replica.version.gap")
                .tag("operation", operation)
                .register(meterRegistry)
                .record(Math.max(versionGap, 0));
    }

    @Override
    public void recordFailoverRehearsal(boolean passed) {
        meterRegistry.counter("oac.dr.failover.rehearsal", "result", passed ? "passed" : "failed").increment();
    }
}