package com.oac.decision.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CheckPermissionRequest(
        @Valid @NotNull SubjectRef subject,
        @NotBlank String action,
        @Valid @NotNull ResourceRef resource,
        @Valid BoundaryContext boundaryContext,
        Map<String, Object> runtimeContext,
        String consistencyToken,
        String requestId,
        String endpointClassification,
        String endpointKey,
        Boolean strictConsistency
) {
}
