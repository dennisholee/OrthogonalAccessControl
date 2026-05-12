package com.oac.decision.adapter.in.web;

import com.oac.decision.application.port.in.DecisionQueryUseCase;
import com.oac.decision.application.port.in.PolicyAdministrationUseCase;
import com.oac.decision.application.port.out.AttributeResolverPort;
import com.oac.decision.application.port.out.AuditEvidencePort;
import com.oac.decision.application.port.out.FailOpenEndpointPolicyPort;
import com.oac.decision.application.port.out.ObservabilityPort;
import com.oac.decision.application.port.out.PolicyRegistryPort;
import com.oac.decision.application.service.DecisionApplicationService;
import com.oac.decision.application.service.PolicyAdministrationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DecisionApplicationConfiguration {

    @Bean
    public DecisionQueryUseCase decisionQueryUseCase(
            PolicyRegistryPort policyRegistryPort,
            AttributeResolverPort attributeResolverPort,
            AuditEvidencePort auditEvidencePort,
            ObservabilityPort observabilityPort,
            FailOpenEndpointPolicyPort failOpenEndpointPolicyPort
    ) {
        return new DecisionApplicationService(
                policyRegistryPort,
                attributeResolverPort,
                auditEvidencePort,
                observabilityPort,
                failOpenEndpointPolicyPort
        );
    }

    @Bean
    public PolicyAdministrationUseCase policyAdministrationUseCase(
            AuditEvidencePort auditEvidencePort,
            ObservabilityPort observabilityPort
    ) {
        return new PolicyAdministrationService(auditEvidencePort, observabilityPort);
    }
}
