package com.oac.decision.model;

import java.time.OffsetDateTime;

public record DisasterRecoveryStatusResponse(
        boolean policyRegistryAvailable,
        boolean auditStoreAvailable,
        boolean policyAuditContinuityIntact,
        boolean failoverRehearsalExecuted,
        boolean failoverRehearsalPassed,
        int activePolicyCount,
        int activePoliciesWithAuditCoverage,
        String decisionCode,
        OffsetDateTime verifiedAt
) {
}
