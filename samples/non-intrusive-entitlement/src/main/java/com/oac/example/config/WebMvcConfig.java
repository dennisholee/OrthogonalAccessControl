package com.oac.example.config;

import com.oac.enforcement.OacEnforcementInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the OacEnforcementInterceptor for all API paths.
 *
 * This configuration only activates when the auto-configuration's
 * interceptor is NOT present — i.e., when oac.enforcement.contract-paths
 * is not configured and no auto-configured interceptor exists.
 *
 * The non-intrusive sample declares entitlements via x-oac-entitlement
 * vendor extensions in the OpenAPI contract. In production, the
 * auto-configuration handles registration automatically via
 * {@code oac.enforcement.contract-paths}. This fallback config
 * is only needed for the annotation-driven pattern or when manually
 * wiring the interceptor.
 */
@Configuration
@ConditionalOnMissingBean(OacEnforcementInterceptor.class)
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // No manual interceptor registration here.
        // When oac.enforcement.contract-paths is configured, the
        // auto-configuration creates the interceptor and registers it.
    }
}