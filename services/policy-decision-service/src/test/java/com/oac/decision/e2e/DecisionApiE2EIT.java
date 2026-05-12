package com.oac.decision.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DecisionApiE2EIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void checkPermissionEndpointReturnsDecisionEnvelopeOverHttp() {
        String url = "http://localhost:" + port + "/v1/decisions/check-permission";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "subject", Map.of("type", "human", "id", "user-10"),
                "action", "read",
                "resource", Map.of("type", "account", "id", "acc-10"),
                "boundaryContext", Map.of(
                        "tenant", "tenant-a",
                        "geography", "us",
                        "market", "retail",
                        "lineOfBusiness", "cards",
                        "channel", "staff"
                )
        );

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("decision")).isEqualTo("DENY");
        assertThat(response.getBody().get("decisionCode")).isEqualTo("DECISION_DEFAULT_DENY");
    }

    @Test
    void checkPermissionEndpointReturnsValidationErrorEnvelopeOverHttp() {
        String url = "http://localhost:" + port + "/v1/decisions/check-permission";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> bodyMissingAction = Map.of(
                "subject", Map.of("type", "human", "id", "user-20"),
                "resource", Map.of("type", "account", "id", "acc-20"),
                "boundaryContext", Map.of(
                        "tenant", "tenant-a",
                        "geography", "us",
                        "market", "retail",
                        "lineOfBusiness", "cards",
                        "channel", "staff"
                )
        );

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(bodyMissingAction, headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("decisionCode")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void checkPermissionEndpointReturnsBoundaryDenyOverHttp() {
        String url = "http://localhost:" + port + "/v1/decisions/check-permission";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "subject", Map.of("type", "human", "id", "user-reader"),
                "action", "read",
                "resource", Map.of("type", "account", "id", "acc-1"),
                "boundaryContext", Map.of(
                        "tenant", "tenant-a",
                        "geography", "us",
                        "market", "retail",
                        "lineOfBusiness", "cards",
                        "channel", "staff"
                ),
                "runtimeContext", Map.of(
                        "resourceTenant", "tenant-a",
                        "resourceGeography", "us",
                        "resourceMarket", "corporate",
                        "resourceLineOfBusiness", "cards",
                        "resourceChannel", "staff"
                )
        );

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("decision")).isEqualTo("DENY");
        assertThat(response.getBody().get("decisionCode")).isEqualTo("DECISION_BOUNDARY_DENY");
    }

    @Test
    void lookupResourcesEndpointReturnsBoundaryFilteredIdsOverHttp() {
        String url = "http://localhost:" + port + "/v1/decisions/lookup-resources";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "subject", Map.of("type", "human", "id", "user-1"),
                "action", "read",
                "resourceType", "account",
                "boundaryContext", Map.of(
                        "tenant", "tenant-a",
                        "geography", "us",
                        "market", "retail",
                        "lineOfBusiness", "cards",
                        "channel", "staff"
                ),
                "pageSize", 10
        );

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        Object ids = response.getBody().get("resourceIds");
        assertThat(ids).isEqualTo(java.util.List.of("acc-1", "acc-2"));
    }

    @Test
    void checkPermissionStrictConsistencyRequiresConsistencyTokenOverHttp() {
        String url = "http://localhost:" + port + "/v1/decisions/check-permission";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "subject", Map.of("type", "human", "id", "user-reader"),
                "action", "read",
                "resource", Map.of("type", "account", "id", "acc-1"),
                "strictConsistency", true,
                "boundaryContext", Map.of(
                        "tenant", "tenant-a",
                        "geography", "us",
                        "market", "retail",
                        "lineOfBusiness", "cards",
                        "channel", "staff"
                ),
                "runtimeContext", Map.of(
                        "resourceTenant", "tenant-a",
                        "resourceGeography", "us",
                        "resourceMarket", "retail",
                        "resourceLineOfBusiness", "cards",
                        "resourceChannel", "staff",
                        "requiredConsistencyToken", "token-1"
                )
        );

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("decision")).isEqualTo("DENY");
        assertThat(response.getBody().get("decisionCode")).isEqualTo("DECISION_CONSISTENCY_TOKEN_REQUIRED");
    }

        @Test
        void lookupResourcesStrictConsistencyReturnsEmptyWhenTokenMissingOverHttp() {
                String url = "http://localhost:" + port + "/v1/decisions/lookup-resources";

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, Object> body = Map.of(
                                "subject", Map.of("type", "human", "id", "user-1"),
                                "action", "read",
                                "resourceType", "account",
                                "strictConsistency", true,
                                "requiredConsistencyToken", "token-1",
                                "boundaryContext", Map.of(
                                                "tenant", "tenant-a",
                                                "geography", "us",
                                                "market", "retail",
                                                "lineOfBusiness", "cards",
                                                "channel", "staff"
                                )
                );

                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                                url,
                                HttpMethod.POST,
                                new HttpEntity<>(body, headers),
                                new ParameterizedTypeReference<>() {}
                );

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().get("resourceIds")).isEqualTo(java.util.List.of());
        }

            @Test
            void checkPermissionStrictConsistencyDeniesWhenRegionalLagSimulatedOverHttp() {
                String url = "http://localhost:" + port + "/v1/decisions/check-permission";

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, Object> body = Map.of(
                        "subject", Map.of("type", "human", "id", "user-reader"),
                        "action", "read",
                        "resource", Map.of("type", "account", "id", "acc-1"),
                        "strictConsistency", true,
                        "boundaryContext", Map.of(
                                "tenant", "tenant-a",
                                "geography", "us",
                                "market", "retail",
                                "lineOfBusiness", "cards",
                                "channel", "staff"
                        ),
                        "runtimeContext", Map.of(
                                "resourceTenant", "tenant-a",
                                "resourceGeography", "us",
                                "resourceMarket", "retail",
                                "resourceLineOfBusiness", "cards",
                                "resourceChannel", "staff",
                                "simulatedRegionalLagMs", 300
                        )
                );

                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        new ParameterizedTypeReference<>() {}
                );

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().get("decision")).isEqualTo("DENY");
                assertThat(response.getBody().get("decisionCode")).isEqualTo("DECISION_REGIONAL_REPLICA_LAG");
            }
}
