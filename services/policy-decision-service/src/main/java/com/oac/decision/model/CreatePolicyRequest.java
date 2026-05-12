package com.oac.decision.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePolicyRequest(
        @NotBlank String name,
        @NotBlank String effect,
        @NotBlank String owner,
        @NotBlank String author,
        @NotNull PolicyRiskLevel riskLevel,
        String definition,
        String idempotencyKey
) {
}