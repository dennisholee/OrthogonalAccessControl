package com.oac.decision.application.service;

import com.oac.decision.application.port.in.PolicyAdministrationUseCase;
import com.oac.decision.application.port.out.AuditEvidencePort;
import com.oac.decision.application.port.out.ObservabilityPort;
import com.oac.decision.model.AuditEventRecord;
import com.oac.decision.model.CreatePolicyRequest;
import com.oac.decision.model.DisasterRecoveryStatusResponse;
import com.oac.decision.model.GovernanceConflictException;
import com.oac.decision.model.PolicyRiskLevel;
import com.oac.decision.model.PolicyResponse;
import com.oac.decision.model.PolicyState;
import com.oac.decision.model.PromotePolicyRequest;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PolicyAdministrationService implements PolicyAdministrationUseCase {

    private static final Map<PolicyState, EnumSet<PolicyState>> ALLOWED_TRANSITIONS = Map.of(
            PolicyState.DRAFT, EnumSet.of(PolicyState.VALIDATED),
            PolicyState.VALIDATED, EnumSet.of(PolicyState.APPROVED),
            PolicyState.APPROVED, EnumSet.of(PolicyState.STAGED),
            PolicyState.STAGED, EnumSet.of(PolicyState.ACTIVE),
            PolicyState.ACTIVE, EnumSet.of(PolicyState.DEPRECATED),
            PolicyState.DEPRECATED, EnumSet.of(PolicyState.RETIRED),
            PolicyState.RETIRED, EnumSet.noneOf(PolicyState.class)
    );

    private final AuditEvidencePort auditEvidencePort;
    private final ObservabilityPort observabilityPort;
    private final Map<String, StoredPolicy> policies = new HashMap<>();

    public PolicyAdministrationService(AuditEvidencePort auditEvidencePort, ObservabilityPort observabilityPort) {
        this.auditEvidencePort = auditEvidencePort;
        this.observabilityPort = observabilityPort;
    }

    @Override
    public synchronized PolicyResponse createPolicyDraft(CreatePolicyRequest request) {
        String policyId = "POL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        StoredPolicy storedPolicy = new StoredPolicy(
                policyId,
                request.owner(),
                request.author(),
                request.riskLevel(),
                PolicyState.DRAFT,
                1
        );
        policies.put(policyId, storedPolicy);

        String evidenceRef = "evidence://policy/" + policyId + "/draft-created";
        auditEvidencePort.append(new AuditEventRecord(
                UUID.randomUUID().toString(),
                "POLICY_DRAFT_CREATED",
                "POLICY",
                policyId,
                request.author(),
                "POLICY_DRAFT_CREATED",
                evidenceRef,
                "INFO",
                OffsetDateTime.now()
        ));
        observabilityPort.recordPolicyLifecycleTransition("NONE", PolicyState.DRAFT.name());

        return new PolicyResponse(
            policyId,
            1,
            PolicyState.DRAFT,
            request.riskLevel(),
            "POLICY_DRAFT_CREATED",
            evidenceRef
        );
    }

    @Override
    public synchronized PolicyResponse promotePolicy(String policyId, PromotePolicyRequest request) {
        StoredPolicy stored = policies.get(policyId);
        if (stored == null) {
            throw new GovernanceConflictException("POLICY_NOT_FOUND", "Policy does not exist: " + policyId);
        }

        if (!ALLOWED_TRANSITIONS.get(stored.state).contains(request.targetState())) {
            throw new GovernanceConflictException(
                    "POLICY_INVALID_STATE_TRANSITION",
                    "Transition not allowed: " + stored.state + " -> " + request.targetState()
            );
        }

        validateLifecycleGates(stored, request);

        if (request.approvers().contains(stored.author)) {
            observabilityPort.recordSecurityAlert("SEPARATION_OF_DUTIES_VIOLATION");
            throw new GovernanceConflictException(
                    "POLICY_SEPARATION_OF_DUTIES_VIOLATION",
                    "Policy author cannot approve their own policy promotion"
            );
        }

        PolicyState fromState = stored.state;
        stored.state = request.targetState();
        stored.version = stored.version + 1;

        String evidenceRef = "evidence://policy/" + policyId + "/promotion/" + stored.version;
        auditEvidencePort.append(new AuditEventRecord(
                UUID.randomUUID().toString(),
                "POLICY_PROMOTED",
                "POLICY",
                policyId,
                request.approvers().get(0),
                "POLICY_PROMOTION_COMPLETED",
                evidenceRef,
                "INFO",
                OffsetDateTime.now()
        ));

        observabilityPort.recordPolicyLifecycleTransition(fromState.name(), request.targetState().name());
        if (request.targetState() == PolicyState.ACTIVE) {
            observabilityPort.recordSecurityAlert("POLICY_ACTIVATION");
        }

        return new PolicyResponse(
            policyId,
            stored.version,
            stored.state,
            stored.riskLevel,
            "POLICY_PROMOTION_COMPLETED",
            evidenceRef
        );
    }

    @Override
    public synchronized List<AuditEventRecord> listAuditEvents(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return auditEvidencePort.findAll();
        }
        return auditEvidencePort.findByEntityId(entityId);
    }

    @Override
    public synchronized DisasterRecoveryStatusResponse verifyDisasterRecoveryContinuity(boolean runFailoverRehearsal) {
        List<AuditEventRecord> allEvents;
        boolean auditStoreAvailable = true;
        try {
            allEvents = auditEvidencePort.findAll();
        } catch (RuntimeException exception) {
            allEvents = List.of();
            auditStoreAvailable = false;
        }

        List<String> activePolicyIds = policies.values().stream()
                .filter(policy -> policy.state == PolicyState.ACTIVE)
                .map(policy -> policy.policyId)
                .toList();

        var auditedPolicyIds = allEvents.stream()
                .filter(event -> "POLICY".equals(event.entityType()))
                .map(AuditEventRecord::entityId)
                .collect(Collectors.toSet());

        int activePoliciesWithAuditCoverage = (int) activePolicyIds.stream()
                .filter(auditedPolicyIds::contains)
                .count();

        boolean policyRegistryAvailable = !policies.isEmpty();
        boolean policyAuditContinuityIntact = activePolicyIds.isEmpty()
                ? auditStoreAvailable
                : activePoliciesWithAuditCoverage == activePolicyIds.size();

        boolean failoverRehearsalExecuted = runFailoverRehearsal;
        boolean failoverRehearsalPassed = !runFailoverRehearsal;
        if (runFailoverRehearsal) {
            String rehearsalEntityId = "dr-rehearsal-" + UUID.randomUUID();
            try {
            auditEvidencePort.append(new AuditEventRecord(
                UUID.randomUUID().toString(),
                "DR_FAILOVER_REHEARSAL",
                "SYSTEM",
                rehearsalEntityId,
                "system",
                "DR_FAILOVER_REHEARSAL_EXECUTED",
                "evidence://dr/rehearsal/" + rehearsalEntityId,
                "INFO",
                OffsetDateTime.now()
            ));
            failoverRehearsalPassed = !auditEvidencePort.findByEntityId(rehearsalEntityId).isEmpty();
            } catch (RuntimeException exception) {
            failoverRehearsalPassed = false;
            }
            observabilityPort.recordFailoverRehearsal(failoverRehearsalPassed);
        }

        String decisionCode = policyAuditContinuityIntact && failoverRehearsalPassed
                ? "DR_CONTINUITY_VERIFIED"
                : "DR_CONTINUITY_GAP_DETECTED";

        if (!policyAuditContinuityIntact || !failoverRehearsalPassed) {
            observabilityPort.recordSecurityAlert("DR_CONTINUITY_GAP");
        }

        return new DisasterRecoveryStatusResponse(
                policyRegistryAvailable,
                auditStoreAvailable,
                policyAuditContinuityIntact,
            failoverRehearsalExecuted,
            failoverRehearsalPassed,
                activePolicyIds.size(),
                activePoliciesWithAuditCoverage,
                decisionCode,
                OffsetDateTime.now()
        );
    }

    private void validateLifecycleGates(StoredPolicy stored, PromotePolicyRequest request) {
        if (request.targetState() == PolicyState.APPROVED
                || request.targetState() == PolicyState.STAGED
                || request.targetState() == PolicyState.ACTIVE) {
            validateSimulationCoverage(stored.riskLevel, request.simulationCoverage());
            validateApprovalQuorum(stored, request);
        }

        if ((request.targetState() == PolicyState.STAGED || request.targetState() == PolicyState.ACTIVE)
                && (request.changeRationale() == null || request.changeRationale().isBlank())) {
            throw new GovernanceConflictException(
                    "POLICY_CHANGE_RATIONALE_REQUIRED",
                    "Change rationale is required for staged/active promotions"
            );
        }

        if (request.targetState() == PolicyState.ACTIVE
                && (request.rollbackReference() == null || request.rollbackReference().isBlank())) {
            throw new GovernanceConflictException(
                    "POLICY_ROLLBACK_REFERENCE_REQUIRED",
                    "Rollback reference is required for active promotion"
            );
        }
    }

    private void validateSimulationCoverage(PolicyRiskLevel riskLevel, Integer simulationCoverage) {
        int requiredCoverage = switch (riskLevel) {
            case LOW -> 80;
            case MEDIUM -> 90;
            case HIGH, CRITICAL -> 95;
        };

        if (simulationCoverage == null || simulationCoverage < requiredCoverage) {
            throw new GovernanceConflictException(
                    "POLICY_SIMULATION_COVERAGE_INSUFFICIENT",
                    "Simulation coverage " + simulationCoverage + " is below required threshold " + requiredCoverage
            );
        }
    }

    private void validateApprovalQuorum(StoredPolicy stored, PromotePolicyRequest request) {
        List<String> approvers = request.approvers();
        List<String> approverRoles = request.approverRoles() == null ? List.of() : request.approverRoles();

        if (!approverRoles.isEmpty() && approverRoles.size() != approvers.size()) {
            throw new GovernanceConflictException(
                    "POLICY_APPROVER_ROLE_MISMATCH",
                    "Approver roles count must match approvers count"
            );
        }

        int minimumApprovers = switch (stored.riskLevel) {
            case LOW, MEDIUM -> 1;
            case HIGH, CRITICAL -> 2;
        };
        if (approvers.size() < minimumApprovers) {
            throw new GovernanceConflictException(
                    "POLICY_APPROVAL_QUORUM_NOT_MET",
                    "Approvers " + approvers.size() + " below minimum " + minimumApprovers
            );
        }

        if (stored.riskLevel == PolicyRiskLevel.MEDIUM && !approvers.contains(stored.owner)) {
            throw new GovernanceConflictException(
                    "POLICY_OWNER_ACK_REQUIRED",
                    "Medium risk promotions require owner acknowledgement"
            );
        }

        if (stored.riskLevel == PolicyRiskLevel.HIGH && !approverRoles.contains("SECURITY_GOVERNANCE")) {
            throw new GovernanceConflictException(
                    "POLICY_REQUIRED_APPROVER_ROLE_MISSING",
                    "High risk promotions require SECURITY_GOVERNANCE approver role"
            );
        }

        if (stored.riskLevel == PolicyRiskLevel.CRITICAL
                && (!approverRoles.contains("SENIOR_SECURITY") || !approverRoles.contains("BUSINESS_CONTROL_OWNER"))) {
            throw new GovernanceConflictException(
                    "POLICY_REQUIRED_APPROVER_ROLE_MISSING",
                    "Critical risk promotions require SENIOR_SECURITY and BUSINESS_CONTROL_OWNER roles"
            );
        }
    }

    private static final class StoredPolicy {
        private final String policyId;
        private final String owner;
        private final String author;
        private final PolicyRiskLevel riskLevel;
        private PolicyState state;
        private int version;

        private StoredPolicy(
                String policyId,
                String owner,
                String author,
                PolicyRiskLevel riskLevel,
                PolicyState state,
                int version
        ) {
            this.policyId = policyId;
            this.owner = owner;
            this.author = author;
            this.riskLevel = riskLevel;
            this.state = state;
            this.version = version;
        }
    }
}