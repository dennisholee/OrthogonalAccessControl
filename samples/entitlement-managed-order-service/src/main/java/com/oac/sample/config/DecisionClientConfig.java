package com.oac.sample.config;

import com.oac.enforcement.DecisionClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestTemplate;

@Configuration
@Profile("!mongodb")
public class DecisionClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public DecisionClient decisionClient(RestTemplate restTemplate,
                                          @Value("${oac.pdp.url:http://localhost:8080}") String pdpUrl) {
        return new RestDecisionClient(restTemplate, pdpUrl);
    }
}