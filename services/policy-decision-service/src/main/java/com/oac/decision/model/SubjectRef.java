package com.oac.decision.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SubjectRef(
        @NotBlank @Pattern(regexp = "human|workload|batch|delegated") String type,
        @NotBlank String id
) {
}
