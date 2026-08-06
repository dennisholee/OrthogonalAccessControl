package com.oac.decision.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PromotePolicyRequest(
        @NotNull PolicyState targetState,
        @NotEmpty List<String> approvers,
        List<String> approverRoles,
        Integer simulationCoverage,
        String changeRationale,
        String rollbackReference,
        String consistencyToken,
        String idempotencyKey,
        String principal
) {
    public PromotePolicyRequest(
            PolicyState targetState,
            List<String> approvers,
            List<String> approverRoles,
            Integer simulationCoverage,
            String changeRationale,
            String rollbackReference,
            String consistencyToken,
            String idempotencyKey
    ) {
        this(targetState, approvers, approverRoles, simulationCoverage, changeRationale,
                rollbackReference, consistencyToken, idempotencyKey, null);
    }
}