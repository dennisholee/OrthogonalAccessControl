package com.oac.decision.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LookupResourcesRequest(
        @Valid @NotNull SubjectRef subject,
        @NotBlank String action,
        @NotBlank String resourceType,
        @Valid @NotNull BoundaryContext boundaryContext,
        String consistencyToken,
        Boolean strictConsistency,
        String requiredConsistencyToken,
        Integer simulatedRegionalLagMs,
        Long replicaVersion,
        Long minimumReplicaVersion,
        @Min(1) @Max(1000) Integer pageSize,
        String pageToken
) {
}
