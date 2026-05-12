package com.oac.decision.adapter.out.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsObservabilityAdapterTest {

    @Test
    void recordsRegionalLagAndReplicaVersionGapMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MetricsObservabilityAdapter adapter = new MetricsObservabilityAdapter(meterRegistry);

        adapter.recordRegionalLag("check-permission", 300);
        adapter.recordRegionalLag("check-permission", 120);
        adapter.recordReplicaVersionGap("lookup-resources", 3);

        DistributionSummary lagSummary = meterRegistry.find("oac.consistency.regional.lag.ms")
                .tag("operation", "check-permission")
                .summary();
        DistributionSummary versionGapSummary = meterRegistry.find("oac.consistency.replica.version.gap")
                .tag("operation", "lookup-resources")
                .summary();

        assertThat(lagSummary).isNotNull();
        assertThat(lagSummary.count()).isEqualTo(2);
        assertThat(lagSummary.totalAmount()).isEqualTo(420.0);
        assertThat(versionGapSummary).isNotNull();
        assertThat(versionGapSummary.count()).isEqualTo(1);
        assertThat(versionGapSummary.totalAmount()).isEqualTo(3.0);
    }

    @Test
    void recordsFailoverRehearsalOutcomeCounters() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MetricsObservabilityAdapter adapter = new MetricsObservabilityAdapter(meterRegistry);

        adapter.recordFailoverRehearsal(true);
        adapter.recordFailoverRehearsal(false);

        Counter passedCounter = meterRegistry.find("oac.dr.failover.rehearsal")
                .tag("result", "passed")
                .counter();
        Counter failedCounter = meterRegistry.find("oac.dr.failover.rehearsal")
                .tag("result", "failed")
                .counter();

        assertThat(passedCounter).isNotNull();
        assertThat(failedCounter).isNotNull();
        assertThat(passedCounter.count()).isEqualTo(1.0);
        assertThat(failedCounter.count()).isEqualTo(1.0);
    }
}
