package com.oac.decision.application.port.out;

public interface FailOpenEndpointPolicyPort {

    boolean isFailOpenApproved(String endpointKey);
}
