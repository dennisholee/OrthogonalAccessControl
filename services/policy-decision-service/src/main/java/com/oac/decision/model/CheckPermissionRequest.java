package com.oac.decision.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Permission check request carrying subject, resource, action and workflow context.
 * <p>
 * This model aligns with the policy architecture (docs/POLICY_ARCHITECTURE.md):
 * <ul>
 *   <li>boundaryContext — seven orthogonal boundary dimensions (Section 3.4)</li>
 *   <li>suppressionFlags — DNC/DNS/litigation-hold/deceased overrides (Section 4.26)</li>
 *   <li>consentVersion + consentAttributes — SubjectConsentRule inputs (Section 4.19)</li>
 *   <li>crossBoundaryJustification — CrossBoundaryRule input (Section 4.10)</li>
 *   <li>principalMemberships — multi-domain membership (Section 4.33)</li>
 * </ul>
 */
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
        Boolean strictConsistency,
        String crossBoundaryJustification,
        String consentVersion,
        Map<String, Object> consentAttributes,
        Map<String, Boolean> suppressionFlags,
        PrincipalMemberships principalMemberships,
        Map<String, String> consistencyTokens
) {

    /** Backward-compatible constructor without the new CDP fields. */
    public CheckPermissionRequest(
            SubjectRef subject,
            String action,
            ResourceRef resource,
            BoundaryContext boundaryContext,
            Map<String, Object> runtimeContext,
            String consistencyToken,
            String requestId,
            String endpointClassification,
            String endpointKey,
            Boolean strictConsistency
    ) {
        this(subject, action, resource, boundaryContext, runtimeContext,
                consistencyToken, requestId, endpointClassification, endpointKey,
                strictConsistency, null, null, null, null, null, null);
    }

    /**
     * Principal multi-domain membership — the set of domain entities a principal is
     * authorised to operate within (docs/POLICY_ARCHITECTURE.md Section 4.33).
     */
    public record PrincipalMemberships(
            List<String> tenants,
            List<String> geographies,
            List<String> markets,
            List<String> linesOfBusiness,
            List<String> channels,
            List<String> purposes,
            List<String> regulatoryRegimes
    ) {
        public PrincipalMemberships {
            tenants = tenants == null ? List.of() : List.copyOf(tenants);
            geographies = geographies == null ? List.of() : List.copyOf(geographies);
            markets = markets == null ? List.of() : List.copyOf(markets);
            linesOfBusiness = linesOfBusiness == null ? List.of() : List.copyOf(linesOfBusiness);
            channels = channels == null ? List.of() : List.copyOf(channels);
            purposes = purposes == null ? List.of() : List.copyOf(purposes);
            regulatoryRegimes = regulatoryRegimes == null ? List.of() : List.copyOf(regulatoryRegimes);
        }
    }
}