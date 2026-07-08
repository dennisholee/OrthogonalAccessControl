package com.oac.enforcement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oac.enforcement.resolver.CompositeSubjectResolver;
import com.oac.enforcement.resolver.HeaderSubjectResolver;
import com.oac.enforcement.resolver.JwtSubjectResolver;
import com.oac.enforcement.resolver.SubjectResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Auto-configuration for the Orthogonal Access Control enforcement library.
 *
 * <p>Core beans (registry, resolvers, interceptor, response advice) are configured here.
 * Optional decorators (caching, resilience, health, observation) are auto-configured
 * only when their respective libraries are on the classpath, via separate
 * auto-configuration classes contributed through
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(OacEntitlementProperties.class)
public class EnforcementAutoConfiguration {

    // ============ Base Decision Client ============

    @Bean
    @ConditionalOnMissingBean
    public DecisionClient decisionClient() {
        return new NoOpDecisionClient();
    }

    // ============ Entitlement Registry ============

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

    // ============ Subject Resolver Beans ============

    @Bean
    @ConditionalOnMissingBean(HeaderSubjectResolver.class)
    public HeaderSubjectResolver headerSubjectResolver(OacEntitlementProperties properties) {
        return new HeaderSubjectResolver(properties.getIdentity());
    }

    @Bean
    @ConditionalOnClass(name = "io.jsonwebtoken.JwtParser")
    @ConditionalOnMissingBean(JwtSubjectResolver.class)
    public JwtSubjectResolver jwtSubjectResolver(OacEntitlementProperties properties) {
        return new JwtSubjectResolver(properties.getIdentity().getJwt().getSchemas());
    }

    @Bean
    @ConditionalOnMissingBean(CompositeSubjectResolver.class)
    @ConditionalOnProperty(prefix = "oac.enforcement.identity", name = "resolver-mode",
                           havingValue = "composite", matchIfMissing = false)
    public CompositeSubjectResolver compositeSubjectResolver(List<SubjectResolver> resolvers) {
        return new CompositeSubjectResolver(resolvers);
    }

    // ============ Interceptor ============

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "oac.enforcement", name = "contract-paths[0]")
    public OacEnforcementInterceptor oacEnforcementInterceptor(
            EntitlementRegistry registry,
            DecisionClient decisionClient,
            OacEntitlementProperties properties,
            List<SubjectResolver> subjectResolvers) {
        SubjectResolver resolver = selectResolver(properties.getIdentity().getResolverMode(), subjectResolvers);
        return new OacEnforcementInterceptor(registry, decisionClient, resolver, properties);
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

    // ============ Response Advice ============

    @Bean
    @ConditionalOnMissingBean
    public FieldMaskResponseAdvice fieldMaskResponseAdvice(
            DecisionClient decisionClient,
            ObjectMapper objectMapper) {
        return new FieldMaskResponseAdvice(decisionClient, objectMapper);
    }

    // ============ Private Helpers ============

    private static SubjectResolver selectResolver(String mode, List<SubjectResolver> resolvers) {
        if ("composite".equals(mode)) {
            for (SubjectResolver r : resolvers) {
                if (r instanceof CompositeSubjectResolver) return r;
            }
        }
        if ("jwt".equals(mode)) {
            for (SubjectResolver r : resolvers) {
                if (r instanceof JwtSubjectResolver) return r;
            }
        }
        for (SubjectResolver r : resolvers) {
            if (r instanceof HeaderSubjectResolver) return r;
        }
        return resolvers.isEmpty() ? null : resolvers.get(0);
    }
}