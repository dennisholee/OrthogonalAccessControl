package com.oac.decision.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DecisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void checkPermissionReturnsDefaultDenyWithDecisionCode() throws Exception {
        String requestBody = """
                {
                  "subject": {"type": "human", "id": "user-1"},
                  "action": "read",
                  "resource": {"type": "account", "id": "acc-1"},
                  "boundaryContext": {
                    "tenant": "tenant-a",
                    "geography": "us",
                    "market": "retail",
                    "lineOfBusiness": "cards",
                    "channel": "staff"
                  }
                }
                """;

        mockMvc.perform(post("/v1/decisions/check-permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.decisionCode").value("DECISION_DEFAULT_DENY"))
                .andExpect(jsonPath("$.explanationRefs[0]").exists());
    }

    @Test
    void checkPermissionMissingActionReturnsValidationErrorEnvelope() throws Exception {
        String requestBody = """
                {
                  "subject": {"type": "human", "id": "user-1"},
                  "resource": {"type": "account", "id": "acc-1"},
                  "boundaryContext": {
                    "tenant": "tenant-a",
                    "geography": "us",
                    "market": "retail",
                    "lineOfBusiness": "cards",
                    "channel": "staff"
                  }
                }
                """;

        mockMvc.perform(post("/v1/decisions/check-permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.decisionCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_ERROR"));
    }

    @Test
    void lookupResourcesReturnsNoOverexposedResourceIds() throws Exception {
        String requestBody = """
                {
                  "subject": {"type": "human", "id": "user-1"},
                  "action": "read",
                  "resourceType": "account",
                  "boundaryContext": {
                    "tenant": "tenant-a",
                    "geography": "us",
                    "market": "retail",
                    "lineOfBusiness": "cards",
                    "channel": "staff"
                  },
                  "pageSize": 50
                }
                """;

        mockMvc.perform(post("/v1/decisions/lookup-resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceIds").isArray())
          .andExpect(jsonPath("$.resourceIds.length()").value(2))
          .andExpect(jsonPath("$.resourceIds[0]").value("acc-1"))
          .andExpect(jsonPath("$.resourceIds[1]").value("acc-2"));
    }

    @Test
    void checkPermissionReturnsAllowWhenPolicyMatches() throws Exception {
        String requestBody = """
                {
                  "subject": {"type": "human", "id": "user-reader"},
                  "action": "read",
                  "resource": {"type": "account", "id": "acc-1"},
                  "boundaryContext": {
                    "tenant": "tenant-a",
                    "geography": "us",
                    "market": "retail",
                    "lineOfBusiness": "cards",
                    "channel": "staff"
                  },
                  "runtimeContext": {
                    "resourceTenant": "tenant-a",
                    "resourceGeography": "us",
                    "resourceMarket": "retail",
                    "resourceLineOfBusiness": "cards",
                    "resourceChannel": "staff"
                  }
                }
                """;

        mockMvc.perform(post("/v1/decisions/check-permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOW"))
                .andExpect(jsonPath("$.decisionCode").value("DECISION_POLICY_ALLOW"));
    }

    @Test
    void checkPermissionReturnsExplicitDenyWhenBlockedRuntimeContextPresent() throws Exception {
        String requestBody = """
                {
                  "subject": {"type": "human", "id": "user-reader"},
                  "action": "read",
                  "resource": {"type": "account", "id": "acc-1"},
                  "boundaryContext": {
                    "tenant": "tenant-a",
                    "geography": "us",
                    "market": "retail",
                    "lineOfBusiness": "cards",
                    "channel": "staff"
                  },
                  "runtimeContext": {
                    "blocked": true
                  }
                }
                """;

        mockMvc.perform(post("/v1/decisions/check-permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.decisionCode").value("DECISION_EXPLICIT_DENY"));
    }

    @Test
    void checkPermissionReturnsMissingBoundaryContextWhenRuntimeContextDoesNotContainResourceBoundaries() throws Exception {
        String requestBody = """
                {
                  "subject": {"type": "human", "id": "user-reader"},
                  "action": "read",
                  "resource": {"type": "account", "id": "acc-1"},
                  "boundaryContext": {
                    "tenant": "tenant-a",
                    "geography": "us",
                    "market": "retail",
                    "lineOfBusiness": "cards",
                    "channel": "staff"
                  }
                }
                """;

        mockMvc.perform(post("/v1/decisions/check-permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.decisionCode").value("DECISION_MISSING_BOUNDARY_CONTEXT"));
    }

    @Test
    void checkPermissionReturnsBoundaryDenyWhenResourceBoundaryMismatches() throws Exception {
        String requestBody = """
                {
                  "subject": {"type": "human", "id": "user-reader"},
                  "action": "read",
                  "resource": {"type": "account", "id": "acc-1"},
                  "boundaryContext": {
                    "tenant": "tenant-a",
                    "geography": "us",
                    "market": "retail",
                    "lineOfBusiness": "cards",
                    "channel": "staff"
                  },
                  "runtimeContext": {
                    "resourceTenant": "tenant-a",
                    "resourceGeography": "us",
                    "resourceMarket": "corporate",
                    "resourceLineOfBusiness": "cards",
                    "resourceChannel": "staff"
                  }
                }
                """;

        mockMvc.perform(post("/v1/decisions/check-permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.decisionCode").value("DECISION_BOUNDARY_DENY"));
    }

    @Test
    void checkPermissionReturnsConsistencyTokenMismatchForCriticalPath() throws Exception {
        String requestBody = """
                {
                  "subject": {"type": "human", "id": "user-reader"},
                  "action": "read",
                  "resource": {"type": "account", "id": "acc-1"},
                  "boundaryContext": {
                    "tenant": "tenant-a",
                    "geography": "us",
                    "market": "retail",
                    "lineOfBusiness": "cards",
                    "channel": "staff"
                  },
                  "consistencyToken": "token-new",
                  "runtimeContext": {
                    "resourceTenant": "tenant-a",
                    "resourceGeography": "us",
                    "resourceMarket": "retail",
                    "resourceLineOfBusiness": "cards",
                    "resourceChannel": "staff",
                    "requiredConsistencyToken": "token-old"
                  }
                }
                """;

        mockMvc.perform(post("/v1/decisions/check-permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.decisionCode").value("DECISION_CONSISTENCY_TOKEN_MISMATCH"));
    }

    @Test
    void checkPermissionReturnsConsistencyTokenMismatchWhenRequiredTokenExistsButRequestTokenMissing() throws Exception {
        String requestBody = """
                {
                  "subject": {"type": "human", "id": "user-reader"},
                  "action": "read",
                  "resource": {"type": "account", "id": "acc-1"},
                  "boundaryContext": {
                    "tenant": "tenant-a",
                    "geography": "us",
                    "market": "retail",
                    "lineOfBusiness": "cards",
                    "channel": "staff"
                  },
                  "runtimeContext": {
                    "resourceTenant": "tenant-a",
                    "resourceGeography": "us",
                    "resourceMarket": "retail",
                    "resourceLineOfBusiness": "cards",
                    "resourceChannel": "staff",
                    "requiredConsistencyToken": "token-42"
                  }
                }
                """;

        mockMvc.perform(post("/v1/decisions/check-permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.decisionCode").value("DECISION_CONSISTENCY_TOKEN_MISMATCH"));
    }

    @Test
    void lookupResourcesReturnsEmptyWhenBoundaryDoesNotMatch() throws Exception {
        String requestBody = """
                {
                  "subject": {"type": "human", "id": "user-1"},
                  "action": "read",
                  "resourceType": "account",
                  "boundaryContext": {
                    "tenant": "tenant-a",
                    "geography": "us",
                    "market": "corporate",
                    "lineOfBusiness": "cards",
                    "channel": "staff"
                  },
                  "pageSize": 50
                }
                """;

        mockMvc.perform(post("/v1/decisions/lookup-resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceIds").isArray())
                .andExpect(jsonPath("$.resourceIds.length()").value(0));
    }

    @Test
    void checkPermissionFailsClosedWhenDependencyOutageAndEndpointIsFailClosed() throws Exception {
        String requestBody = """
                {
                  "subject": {"type": "human", "id": "user-reader"},
                  "action": "read",
                  "resource": {"type": "account", "id": "acc-1"},
                  "endpointClassification": "FAIL_CLOSED",
                  "boundaryContext": {
                    "tenant": "tenant-a",
                    "geography": "us",
                    "market": "retail",
                    "lineOfBusiness": "cards",
                    "channel": "staff"
                  },
                  "runtimeContext": {
                    "resourceTenant": "tenant-a",
                    "resourceGeography": "us",
                    "resourceMarket": "retail",
                    "resourceLineOfBusiness": "cards",
                    "resourceChannel": "staff",
                    "dependencyHealthy": false
                  }
                }
                """;

        mockMvc.perform(post("/v1/decisions/check-permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.decisionCode").value("DECISION_FAIL_CLOSED_DEPENDENCY_OUTAGE"));
    }

    @Test
    void checkPermissionAllowsFailOpenWhenOutageAndClassificationIsEligible() throws Exception {
        String requestBody = """
                {
                  "subject": {"type": "human", "id": "user-reader"},
                  "action": "read",
                  "resource": {"type": "account", "id": "acc-1"},
                  "endpointClassification": "FAIL_OPEN",
                  "endpointKey": "account:read",
                  "boundaryContext": {
                    "tenant": "tenant-a",
                    "geography": "us",
                    "market": "retail",
                    "lineOfBusiness": "cards",
                    "channel": "staff"
                  },
                  "runtimeContext": {
                    "resourceTenant": "tenant-a",
                    "resourceGeography": "us",
                    "resourceMarket": "retail",
                    "resourceLineOfBusiness": "cards",
                    "resourceChannel": "staff",
                    "dependencyHealthy": false,
                    "failOpenReadOnly": true,
                    "failOpenNonSensitive": true,
                    "failOpenBoundarySafe": true
                  }
                }
                """;

        mockMvc.perform(post("/v1/decisions/check-permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ALLOW"))
                .andExpect(jsonPath("$.decisionCode").value("DECISION_FAIL_OPEN_ALLOWED"));
    }

    @Test
    void checkPermissionDeniesFailOpenWhenEndpointNotApproved() throws Exception {
        String requestBody = """
                {
                  "subject": {"type": "human", "id": "user-reader"},
                  "action": "read",
                  "resource": {"type": "account", "id": "acc-1"},
                  "endpointClassification": "FAIL_OPEN",
                  "endpointKey": "payment:read",
                  "boundaryContext": {
                    "tenant": "tenant-a",
                    "geography": "us",
                    "market": "retail",
                    "lineOfBusiness": "cards",
                    "channel": "staff"
                  },
                  "runtimeContext": {
                    "resourceTenant": "tenant-a",
                    "resourceGeography": "us",
                    "resourceMarket": "retail",
                    "resourceLineOfBusiness": "cards",
                    "resourceChannel": "staff",
                    "dependencyHealthy": false,
                    "failOpenReadOnly": true,
                    "failOpenNonSensitive": true,
                    "failOpenBoundarySafe": true
                  }
                }
                """;

        mockMvc.perform(post("/v1/decisions/check-permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.decisionCode").value("DECISION_FAIL_OPEN_ENDPOINT_NOT_APPROVED"));
    }

    @Test
    void checkPermissionRequiresConsistencyTokenWhenStrictConsistencyIsEnabled() throws Exception {
        String requestBody = """
                {
                  "subject": {"type": "human", "id": "user-reader"},
                  "action": "read",
                  "resource": {"type": "account", "id": "acc-1"},
                  "strictConsistency": true,
                  "boundaryContext": {
                    "tenant": "tenant-a",
                    "geography": "us",
                    "market": "retail",
                    "lineOfBusiness": "cards",
                    "channel": "staff"
                  },
                  "runtimeContext": {
                    "resourceTenant": "tenant-a",
                    "resourceGeography": "us",
                    "resourceMarket": "retail",
                    "resourceLineOfBusiness": "cards",
                    "resourceChannel": "staff",
                    "requiredConsistencyToken": "token-expected"
                  }
                }
                """;

        mockMvc.perform(post("/v1/decisions/check-permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.decisionCode").value("DECISION_CONSISTENCY_TOKEN_REQUIRED"));
    }

    @Test
    void checkPermissionDeniesWhenRegionalReplicaLagExceedsThresholdInStrictMode() throws Exception {
        String requestBody = """
                {
                  "subject": {"type": "human", "id": "user-reader"},
                  "action": "read",
                  "resource": {"type": "account", "id": "acc-1"},
                  "strictConsistency": true,
                  "boundaryContext": {
                    "tenant": "tenant-a",
                    "geography": "us",
                    "market": "retail",
                    "lineOfBusiness": "cards",
                    "channel": "staff"
                  },
                  "runtimeContext": {
                    "resourceTenant": "tenant-a",
                    "resourceGeography": "us",
                    "resourceMarket": "retail",
                    "resourceLineOfBusiness": "cards",
                    "resourceChannel": "staff",
                    "simulatedRegionalLagMs": 300
                  }
                }
                """;

        mockMvc.perform(post("/v1/decisions/check-permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("DENY"))
                .andExpect(jsonPath("$.decisionCode").value("DECISION_REGIONAL_REPLICA_LAG"));
    }

    @Test
    void lookupResourcesReturnsEmptyWhenStrictConsistencyTokenMissing() throws Exception {
        String requestBody = """
                {
                  "subject": {"type": "human", "id": "user-1"},
                  "action": "read",
                  "resourceType": "account",
                  "strictConsistency": true,
                  "requiredConsistencyToken": "token-1",
                  "boundaryContext": {
                    "tenant": "tenant-a",
                    "geography": "us",
                    "market": "retail",
                    "lineOfBusiness": "cards",
                    "channel": "staff"
                  },
                  "pageSize": 50
                }
                """;

        mockMvc.perform(post("/v1/decisions/lookup-resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceIds").isArray())
                .andExpect(jsonPath("$.resourceIds.length()").value(0));
    }

    @Test
    void lookupResourcesReturnsEmptyWhenStrictConsistencyReplicaVersionIsStale() throws Exception {
        String requestBody = """
                {
                  "subject": {"type": "human", "id": "user-1"},
                  "action": "read",
                  "resourceType": "account",
                  "strictConsistency": true,
                  "consistencyToken": "token-1",
                  "requiredConsistencyToken": "token-1",
                  "replicaVersion": 11,
                  "minimumReplicaVersion": 12,
                  "boundaryContext": {
                    "tenant": "tenant-a",
                    "geography": "us",
                    "market": "retail",
                    "lineOfBusiness": "cards",
                    "channel": "staff"
                  },
                  "pageSize": 50
                }
                """;

        mockMvc.perform(post("/v1/decisions/lookup-resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceIds").isArray())
                .andExpect(jsonPath("$.resourceIds.length()").value(0));
    }
}
