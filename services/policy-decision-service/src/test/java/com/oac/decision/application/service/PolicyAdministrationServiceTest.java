package com.oac.decision.application.service;

import com.oac.decision.application.port.out.AuditEvidencePort;
import com.oac.decision.application.port.out.ObservabilityPort;
import com.oac.decision.model.AuditEventRecord;
import com.oac.decision.model.DisasterRecoveryStatusResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyAdministrationServiceTest {

    @Test
    void verifyDisasterRecoveryContinuityReportsGapWhenRehearsalWriteFails() {
        FailingRehearsalAuditEvidencePort auditPort = new FailingRehearsalAuditEvidencePort();
        CapturingObservabilityPort observabilityPort = new CapturingObservabilityPort();
        PolicyAdministrationService service = new PolicyAdministrationService(auditPort, observabilityPort);

        DisasterRecoveryStatusResponse response = service.verifyDisasterRecoveryContinuity(true);

        assertThat(response.policyRegistryAvailable()).isFalse();
        assertThat(response.auditStoreAvailable()).isTrue();
        assertThat(response.policyAuditContinuityIntact()).isTrue();
        assertThat(response.failoverRehearsalExecuted()).isTrue();
        assertThat(response.failoverRehearsalPassed()).isFalse();
        assertThat(response.decisionCode()).isEqualTo("DR_CONTINUITY_GAP_DETECTED");
        assertThat(observabilityPort.securityAlerts).contains("DR_CONTINUITY_GAP");
        assertThat(observabilityPort.rehearsalOutcomes).contains(false);
    }

    private static final class FailingRehearsalAuditEvidencePort implements AuditEvidencePort {

        @Override
        public void append(AuditEventRecord event) {
            if ("DR_FAILOVER_REHEARSAL".equals(event.eventType())) {
                throw new RuntimeException("Simulated rehearsal write failure");
            }
        }

        @Override
        public List<AuditEventRecord> findByEntityId(String entityId) {
            return List.of();
        }

        @Override
        public List<AuditEventRecord> findAll() {
            return List.of();
        }
    }

    private static final class CapturingObservabilityPort implements ObservabilityPort {

        private final List<String> securityAlerts = new ArrayList<>();
        private final List<Boolean> rehearsalOutcomes = new ArrayList<>();

        @Override
        public void recordDecision(String decisionCode) {
        }

        @Override
        public void recordPolicyLifecycleTransition(String fromState, String toState) {
        }

        @Override
        public void recordSecurityAlert(String alertType) {
            securityAlerts.add(alertType);
        }

        @Override
        public void recordRegionalLag(String operation, long lagMs) {
        }

        @Override
        public void recordReplicaVersionGap(String operation, long versionGap) {
        }

        @Override
        public void recordFailoverRehearsal(boolean passed) {
            rehearsalOutcomes.add(passed);
        }
    }
}
