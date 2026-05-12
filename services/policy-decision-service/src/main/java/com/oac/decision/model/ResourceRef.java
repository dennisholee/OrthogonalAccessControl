package com.oac.decision.model;

import jakarta.validation.constraints.NotBlank;

public record ResourceRef(
        @NotBlank String type,
        @NotBlank String id
) {
}
