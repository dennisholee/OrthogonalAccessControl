package com.oac.decision.model;

import jakarta.validation.constraints.NotBlank;

/**
 * The seven orthogonal boundary dimensions that scope every authorisation decision.
 * <p>
 * Five dimensions (tenant, geography, market, lineOfBusiness, channel) are core.
 * {@code purpose} and {@code regulatoryRegime} are CDP-required dimensions that
 * are optional for backward compatibility — when absent, matching and validation
 * rules do not filter on those dimensions.
 * <p>
 * See docs/POLICY_ARCHITECTURE.md Sections 3.4, 4.17 and 4.21.
 */
public record BoundaryContext(
        @NotBlank String tenant,
        @NotBlank String geography,
        @NotBlank String market,
        @NotBlank String lineOfBusiness,
        @NotBlank String channel,
        String purpose,
        String regulatoryRegime
) {

    /** Backward-compatible constructor for the original 5-dimension boundary context. */
    public BoundaryContext(
            String tenant,
            String geography,
            String market,
            String lineOfBusiness,
            String channel
    ) {
        this(tenant, geography, market, lineOfBusiness, channel, null, null);
    }

    /** Convenience constructor for all 7 dimensions typically used in tests. */
    public static BoundaryContext of(
            String tenant,
            String geography,
            String market,
            String lineOfBusiness,
            String channel,
            String purpose,
            String regulatoryRegime
    ) {
        return new BoundaryContext(tenant, geography, market, lineOfBusiness, channel, purpose, regulatoryRegime);
    }
}