package com.oac.enforcement;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@EnableConfigurationProperties(OacEntitlementProperties.class)
public class EnforcementAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DecisionClient decisionClient() {
        return new NoOpDecisionClient();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "oac.enforcement", name = "contract-paths[0]")
    public EntitlementRegistry entitlementRegistry(
            OacEntitlementProperties properties,
            org.springframework.core.io.ResourceLoader resourceLoader) {
        return new EntitlementRegistry(
                properties.getContractPaths(),
                resourceLoader,
                properties.isFailClosed()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "oac.enforcement", name = "contract-paths[0]")
    public OacEnforcementInterceptor oacEnforcementInterceptor(
            EntitlementRegistry registry,
            DecisionClient decisionClient,
            OacEntitlementProperties properties) {
        return new OacEnforcementInterceptor(registry, decisionClient, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "oac.enforcement", name = "contract-paths[0]")
    public WebMvcConfigurer oacWebMvcConfigurer(OacEnforcementInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor)
                        .addPathPatterns("/**");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public FieldMaskResponseAdvice fieldMaskResponseAdvice(
            DecisionClient decisionClient,
            ObjectMapper objectMapper) {
        return new FieldMaskResponseAdvice(decisionClient, objectMapper);
    }
}