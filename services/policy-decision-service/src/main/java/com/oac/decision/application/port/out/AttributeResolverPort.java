package com.oac.decision.application.port.out;

import com.oac.decision.model.CheckPermissionRequest;

import java.util.Map;

public interface AttributeResolverPort {

    Map<String, Object> resolve(CheckPermissionRequest request);
}