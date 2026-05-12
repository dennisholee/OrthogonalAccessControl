package com.oac.decision.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PolicyAdministrationE2EIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void policyAdministrationLifecycleAndAuditWorkOverHttp() {
        String createUrl = "http://localhost:" + port + "/v1/admin/policies";
        String promoteUrlPrefix = "http://localhost:" + port + "/v1/admin/policies/";
        String auditUrl = "http://localhost:" + port + "/v1/admin/audit-events";
        String continuityUrl = "http://localhost:" + port + "/v1/admin/recovery/continuity";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> createBody = Map.of(
                "name", "POL.RETAIL.ACCOUNT.VIEW.ALLOW.v2",
                "effect", "ALLOW",
                "owner", "policy-owner",
                "author", "maker-user",
                "riskLevel", "LOW",
                "definition", "allow view"
        );

        ResponseEntity<Map<String, Object>> createResponse = restTemplate.exchange(
                createUrl,
                HttpMethod.POST,
                new HttpEntity<>(createBody, headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().get("state")).isEqualTo("DRAFT");

        String policyId = (String) createResponse.getBody().get("policyId");
        assertThat(policyId).isNotBlank();

        Map<String, Object> promoteBody = Map.of(
                "targetState", "VALIDATED",
                "approvers", List.of("checker-user")
        );

        ResponseEntity<Map<String, Object>> promoteResponse = restTemplate.exchange(
                promoteUrlPrefix + policyId + "/promote",
                HttpMethod.POST,
                new HttpEntity<>(promoteBody, headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(promoteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(promoteResponse.getBody()).isNotNull();
        assertThat(promoteResponse.getBody().get("state")).isEqualTo("VALIDATED");

        ResponseEntity<Map<String, Object>> approvedResponse = restTemplate.exchange(
                promoteUrlPrefix + policyId + "/promote",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "targetState", "APPROVED",
                        "approvers", List.of("checker-user"),
                        "simulationCoverage", 85
                ), headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(approvedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approvedResponse.getBody()).isNotNull();
        assertThat(approvedResponse.getBody().get("state")).isEqualTo("APPROVED");

        ResponseEntity<Map<String, Object>> stagedResponse = restTemplate.exchange(
                promoteUrlPrefix + policyId + "/promote",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "targetState", "STAGED",
                        "approvers", List.of("checker-user"),
                        "simulationCoverage", 85,
                        "changeRationale", "stage for dr"
                ), headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(stagedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stagedResponse.getBody()).isNotNull();
        assertThat(stagedResponse.getBody().get("state")).isEqualTo("STAGED");

        ResponseEntity<Map<String, Object>> activeResponse = restTemplate.exchange(
                promoteUrlPrefix + policyId + "/promote",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "targetState", "ACTIVE",
                        "approvers", List.of("checker-user"),
                        "simulationCoverage", 85,
                        "changeRationale", "activate for dr",
                        "rollbackReference", "rbk-001"
                ), headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(activeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activeResponse.getBody()).isNotNull();
        assertThat(activeResponse.getBody().get("state")).isEqualTo("ACTIVE");

        ResponseEntity<List<Map<String, Object>>> auditResponse = restTemplate.exchange(
                auditUrl + "?entityId=" + policyId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(auditResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(auditResponse.getBody()).isNotEmpty();
        assertThat(auditResponse.getBody().get(0).get("entityId")).isEqualTo(policyId);

        ResponseEntity<Map<String, Object>> continuityResponse = restTemplate.exchange(
                continuityUrl,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(continuityResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(continuityResponse.getBody()).isNotNull();
        assertThat(continuityResponse.getBody()).containsKey("decisionCode");
        assertThat(continuityResponse.getBody()).containsKey("verifiedAt");
                assertThat(continuityResponse.getBody().get("activePolicyCount")).isEqualTo(1);
                assertThat(continuityResponse.getBody().get("activePoliciesWithAuditCoverage")).isEqualTo(1);
                assertThat(continuityResponse.getBody().get("policyAuditContinuityIntact")).isEqualTo(true);

        ResponseEntity<Map<String, Object>> rehearsalResponse = restTemplate.exchange(
                continuityUrl + "?rehearsal=true",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(rehearsalResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rehearsalResponse.getBody()).isNotNull();
        assertThat(rehearsalResponse.getBody().get("failoverRehearsalExecuted")).isEqualTo(true);
        assertThat(rehearsalResponse.getBody().get("failoverRehearsalPassed")).isEqualTo(true);
    }
}
