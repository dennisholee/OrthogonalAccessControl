package com.oac.decision.adapter.out.attribute;

import com.oac.decision.application.port.out.AttributeResolverPort;
import com.oac.decision.model.CheckPermissionRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class InMemoryAttributeResolverAdapter implements AttributeResolverPort {

    @Override
    public Map<String, Object> resolve(CheckPermissionRequest request) {
        return request.runtimeContext() == null ? Map.of() : request.runtimeContext();
    }
}