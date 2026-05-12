package com.oac.decision.application.port.out;

public interface ObservabilityPort {

    void recordDecision(String decisionCode);

    void recordPolicyLifecycleTransition(String fromState, String toState);

    void recordSecurityAlert(String alertType);

    void recordRegionalLag(String operation, long lagMs);

    void recordReplicaVersionGap(String operation, long versionGap);

    void recordFailoverRehearsal(boolean passed);
}