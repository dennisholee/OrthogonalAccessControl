package com.oac.decision.application.port.in;

import com.oac.decision.model.CheckPermissionRequest;
import com.oac.decision.model.CheckPermissionResponse;
import com.oac.decision.model.LookupResourcesRequest;
import com.oac.decision.model.LookupResourcesResponse;

public interface DecisionQueryUseCase {

    CheckPermissionResponse checkPermission(CheckPermissionRequest request);

    LookupResourcesResponse lookupResources(LookupResourcesRequest request);
}
