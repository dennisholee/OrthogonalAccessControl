package com.oac.enforcement;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class EnforcementAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DecisionClient decisionClient() {
        return new NoOpDecisionClient();
    }
}
