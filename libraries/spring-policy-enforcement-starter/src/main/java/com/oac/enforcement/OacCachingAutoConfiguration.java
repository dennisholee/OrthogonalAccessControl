package com.oac.enforcement;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the CachingDecisionClient decorator.
 * Activated when {@code spring.cache.cache-names} includes {@code oac.decisions}.
 */
@AutoConfiguration
@ConditionalOnClass(CacheManager.class)
@ConditionalOnProperty(prefix = "oac.enforcement", name = "caching-enabled",
                       havingValue = "true", matchIfMissing = true)
public class OacCachingAutoConfiguration {

    @Bean
    @ConditionalOnBean(CacheManager.class)
    public CachingDecisionClient cachingDecisionClient(
            DecisionClient delegate, CacheManager cacheManager) {
        return new CachingDecisionClient(delegate, cacheManager);
    }
}