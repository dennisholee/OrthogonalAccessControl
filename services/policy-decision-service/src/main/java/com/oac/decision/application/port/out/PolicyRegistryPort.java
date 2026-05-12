package com.oac.decision.application.port.out;

import com.oac.decision.model.CheckPermissionRequest;
import com.oac.decision.model.LookupResourcesRequest;

import java.util.List;

public interface PolicyRegistryPort {

    List<String> findMatchedPolicies(CheckPermissionRequest request);

    List<String> findAuthorizedResourceIds(LookupResourcesRequest request);
}
