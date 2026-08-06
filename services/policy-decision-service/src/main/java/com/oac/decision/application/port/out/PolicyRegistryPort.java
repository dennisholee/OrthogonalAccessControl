package com.oac.decision.application.port.out;

import com.oac.decision.model.CheckPermissionRequest;
import com.oac.decision.model.LookupResourcesRequest;

import java.util.List;
import java.util.Map;

public interface PolicyRegistryPort {

    List<String> findMatchedPolicies(CheckPermissionRequest request);

    /**
     * Returns the names of matched ACTIVE policies whose certification is past due
     * (Section 4.9) — i.e. {@code nextCertificationDate} in the past with no active waiver.
     * The PDP must emit a WARN audit event for each such policy; the decision itself is
     * unaffected until the PAP moves the policy to RESTRICTED.
     */
    List<String> findExpiredCertificationPolicies(CheckPermissionRequest request);

    List<String> findAuthorizedResourceIds(LookupResourcesRequest request);

    /** Returns the active policy version string, e.g. "v3" or "v0" if none active. */
    default String getActiveVersion() {
        return "v0";
    }

    /**
     * Retrieves field-level masks defined in matched policies.
     * Returns a list of field->level entries (e.g. {field: "customer.email", level: "MASK"}).
     */
    default List<Map<String, String>> findFieldMasks(CheckPermissionRequest request) {
        return List.of();
    }
}