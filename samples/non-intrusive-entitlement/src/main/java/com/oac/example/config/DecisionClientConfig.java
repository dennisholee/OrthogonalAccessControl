package com.oac.example.config;

import com.oac.enforcement.DecisionClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Configuration
public class DecisionClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public DecisionClient decisionClient(RestTemplate restTemplate,
                                          @Value("${oac.pdp.url:http://localhost:8080}") String pdpUrl) {
        return new GeneratedDecisionClient(restTemplate, pdpUrl);
    }

    /**
     * HTTP-based DecisionClient that calls the PDP REST API.
     */
    static class GeneratedDecisionClient implements DecisionClient {

        private final RestTemplate restTemplate;
        private final String baseUrl;

        GeneratedDecisionClient(RestTemplate restTemplate, String baseUrl) {
            this.restTemplate = restTemplate;
            this.baseUrl = baseUrl;
        }

        @Override
        public boolean checkPermission(String subjectId, String action, String resourceId) {
            String url = baseUrl + "/v1/decisions/check-permission";

            // Parse resourceId like "order/ORD-001" into type/id
            String resourceType = resourceId.contains("/")
                    ? resourceId.substring(0, resourceId.indexOf("/"))
                    : "resource";
            String resourceIdOnly = resourceId.contains("/")
                    ? resourceId.substring(resourceId.indexOf("/") + 1)
                    : resourceId;

            Map<String, Object> body = Map.of(
                    "subject", Map.of("type", "human", "id", subjectId),
                    "action", action.toLowerCase(),
                    "resource", Map.of("type", resourceType, "id", resourceIdOnly),
                    "boundaryContext", Map.of(
                            "tenant", "acme-corp",
                            "geography", "global",
                            "market", "enterprise",
                            "lineOfBusiness", "ecommerce",
                            "channel", "staff"
                    )
            );

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
                if (response != null) {
                    String decision = (String) response.get("decision");
                    return "ALLOW".equals(decision);
                }
            } catch (Exception e) {
                java.util.logging.Logger.getLogger(getClass().getName())
                        .warning("PDP call failed: " + e.getMessage());
            }
            return false;
        }
    }
}