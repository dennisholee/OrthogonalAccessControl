package com.oac.enforcement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * {@link DecisionClient} implementation that calls a remote PDP service
 * using Spring 6's {@link RestClient}.
 *
 * <p>Supports both synchronous CheckPermission and can be extended
 * for LookupResources in the future.</p>
 *
 * <p>Configured via {@code oac.enforcement.pdp-url} in application.yml.</p>
 */
public class RestClientDecisionClient implements DecisionClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientDecisionClient.class);

    private final RestClient restClient;
    private final String pdpUrl;

    /**
     * Creates a new RestClientDecisionClient.
     *
     * @param restClient the Spring RestClient to use for HTTP calls
     * @param pdpUrl     the base URL of the PDP (e.g., {@code http://pdp:8080})
     */
    public RestClientDecisionClient(RestClient restClient, String pdpUrl) {
        this.restClient = restClient;
        this.pdpUrl = pdpUrl.endsWith("/") ? pdpUrl.substring(0, pdpUrl.length() - 1) : pdpUrl;
    }

    @Override
    public boolean checkPermission(String subjectId, String action, String resourceId) {
        Map<String, String> request = Map.of(
                "subjectId", subjectId,
                "action", action,
                "resourceId", resourceId
        );

        try {
            Map<?, ?> response = restClient.post()
                    .uri(pdpUrl + "/v1/decisions/check-permission")
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                log.warn("OAC PDP returned empty response for {} {} {}", subjectId, action, resourceId);
                return false;
            }

            Object decisionObj = response.get("decision");
            boolean allowed = "ALLOW".equalsIgnoreCase(decisionObj != null ? decisionObj.toString() : null);
            log.debug("OAC PDP decision: {} for {} {} {} -> allowed={}",
                    decisionObj, subjectId, action, resourceId, allowed);
            return allowed;

        } catch (Exception e) {
            log.error("OAC PDP call failed for {} {} {}: {}", subjectId, action, resourceId, e.getMessage());
            return false;
        }
    }
}