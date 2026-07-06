package com.oac.enforcement;

import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Reference implementation showing how to wrap an OpenAPI-generated API client
 * with the OAC DecisionClient interface.
 *
 * When using openapi-generator-maven-plugin with the enhanced contracts/decision-api.yaml,
 * the generated CheckPermissionApi interface would be injected here:
 *
 * {@code
 * @GeneratedDecisionClient
 * public class GeneratedDecisionClient implements DecisionClient {
 *
 *     private final CheckPermissionApi api;
 *
 *     public GeneratedDecisionClient(CheckPermissionApi api) {
 *         this.api = api;
 *     }
 *
 *     // Generated CheckPermissionResponse includes attributeAccessMap,
 *     // obligations, and consistencyToken — all enriched by the enhanced YAML contract.
 * }
 * }
 *
 * @see <a href="file:contracts/decision-api.yaml">Enhanced decision API contract</a>
 * @see <a href="file:openapi-generator-config.yml">Generator configuration</a>
 */
public class GeneratedDecisionClient implements DecisionClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public GeneratedDecisionClient(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public boolean checkPermission(String subjectId, String action, String resourceId) {
        String url = baseUrl + "/v1/decisions/check-permission";

        Map<String, Object> body = Map.of(
                "subject", Map.of("type", "human", "id", subjectId),
                "action", action,
                "resource", parseResource(resourceId),
                "boundaryContext", Map.of(
                        "tenant", "*",
                        "geography", "*",
                        "market", "*",
                        "lineOfBusiness", "*",
                        "channel", "staff"
                )
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            if (response != null) {
                // The enhanced CheckPermissionResponse now includes:
                // - attributeAccessMap (field-level access levels)
                // - obligations (obligations to enforce)
                // - consistencyToken (causal ordering)
                String decision = (String) response.get("decision");
                return "ALLOW".equals(decision) || "ALLOW_WITH_CAVEATS".equals(decision);
            }
        } catch (Exception e) {
            // Log and deny on failure (fail-closed default)
            java.util.logging.Logger.getLogger(GeneratedDecisionClient.class.getName())
                    .warning("PDP call failed: " + e.getMessage());
        }
        return false;
    }

    private Map<String, String> parseResource(String resourceId) {
        if (resourceId.contains("/")) {
            String[] parts = resourceId.split("/", 2);
            return Map.of("type", parts[0], "id", parts[1]);
        }
        return Map.of("type", "resource", "id", resourceId);
    }
}