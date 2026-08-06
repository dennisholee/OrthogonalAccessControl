package com.oac.decision.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreatePolicyRequest(
        @NotBlank String name,
        @NotBlank String effect,
        @NotBlank String owner,
        @NotBlank String author,
        @NotNull PolicyRiskLevel riskLevel,
        String definition,
        String idempotencyKey,
        String subjectType,
        List<PolicyCondition> conditions
) {
    public CreatePolicyRequest(
            String name,
            String effect,
            String owner,
            String author,
            PolicyRiskLevel riskLevel,
            String definition,
            String idempotencyKey
    ) {
        this(name, effect, owner, author, riskLevel, definition, idempotencyKey, null, null);
    }
}