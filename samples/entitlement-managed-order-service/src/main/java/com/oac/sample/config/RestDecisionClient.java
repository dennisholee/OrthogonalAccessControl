package com.oac.sample.config;

import com.oac.enforcement.DecisionClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST implementation of the DecisionClient that calls the Policy Decision Service (PDP).
 * Sends CheckPermission requests and interprets the ALLOW/DENY decision.
 */
public class RestDecisionClient implements DecisionClient {

    private final RestTemplate restTemplate;
    private final String pdpBaseUrl;

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(RestDecisionClient.class);

    public RestDecisionClient(RestTemplate restTemplate, String pdpBaseUrl) {
        this.restTemplate = restTemplate;
        this.pdpBaseUrl = pdpBaseUrl;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean checkPermission(String subjectId, String action, String resourceId) {
        String url = pdpBaseUrl + "/v1/decisions/check-permission";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String resourceType = resourceId.contains("/")
                ? resourceId.substring(0, resourceId.indexOf("/"))
                : "order";

        // Normalize action to lowercase to match PDP policy documents
        String normalizedAction = action.toLowerCase();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subject", createMap("type", "human", "id", subjectId));
        body.put("action", normalizedAction);
        body.put("resource", createMap("type", resourceType, "id", resourceId));
        body.put("boundaryContext", createBoundaryContext());

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response.getBody() != null) {
                String decision = (String) response.getBody().get("decision");
                log.debug("PDP decision for {} {} {}: {}",
                        subjectId, normalizedAction, resourceId, decision);
                return "ALLOW".equals(decision);
            }
        } catch (Exception e) {
            log.warn("PDP call failed for {} {} {}: {}",
                    subjectId, normalizedAction, resourceId, e.getMessage());
        }
        return false;
    }

    /**
     * Extended check that returns enriched decision info including field masks.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> checkPermissionWithDetails(
            String subjectId, String action, String resourceId,
            Map<String, Object> boundaryOverride) {

        String url = pdpBaseUrl + "/v1/decisions/check-permission";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String normalizedAction = action.toLowerCase();

        Map<String, Object> boundary = boundaryOverride != null ? boundaryOverride : createBoundaryContext();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subject", createMap("type", "human", "id", subjectId));
        body.put("action", normalizedAction);
        body.put("resource", createMap("type", "order", "id", resourceId));
        body.put("boundaryContext", boundary);

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            return response.getBody();
        } catch (Exception e) {
            log.warn("PDP detailed check failed: {}", e.getMessage());
            return Map.of("decision", "DENY", "decisionCode", "PDP_UNAVAILABLE");
        }
    }

    private static Map<String, Object> createBoundaryContext() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("tenant", "acme-corp");
        ctx.put("geography", "global");
        ctx.put("market", "enterprise");
        ctx.put("lineOfBusiness", "ecommerce");
        ctx.put("channel", "staff");
        return ctx;
    }

    private static Map<String, Object> createMap(String k1, String v1, String k2, String v2) {
        Map<String, Object> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }
}
