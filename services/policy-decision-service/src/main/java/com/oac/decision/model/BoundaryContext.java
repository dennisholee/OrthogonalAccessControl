package com.oac.decision.model;

import jakarta.validation.constraints.NotBlank;

public record BoundaryContext(
        @NotBlank String tenant,
        @NotBlank String geography,
        @NotBlank String market,
        @NotBlank String lineOfBusiness,
        @NotBlank String channel
) {
}
