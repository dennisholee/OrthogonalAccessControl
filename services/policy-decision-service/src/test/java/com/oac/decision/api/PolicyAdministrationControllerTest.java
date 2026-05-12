package com.oac.decision.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PolicyAdministrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createDraftAndPromotePolicyWithIndependentApprover() throws Exception {
        String createBody = """
                {
                  "name": "POL.RETAIL.ACCOUNT.VIEW.ALLOW.v1",
                  "effect": "ALLOW",
                  "owner": "policy-owner",
                  "author": "maker-user",
                                                                        "riskLevel": "LOW",
                  "definition": "allow retail account view"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/v1/admin/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andReturn();

        String response = createResult.getResponse().getContentAsString();
        String policyId = response.split("\"policyId\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/v1/admin/policies/" + policyId + "/promote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetState": "VALIDATED",
                                  "approvers": ["checker-user"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("VALIDATED"))
                .andExpect(jsonPath("$.decisionCode").value("POLICY_PROMOTION_COMPLETED"));
    }

    @Test
    void promotePolicyRejectsMakerCheckerViolation() throws Exception {
        String createBody = """
                {
                  "name": "POL.RETAIL.ACCOUNT.APPROVE.ALLOW.v1",
                  "effect": "ALLOW",
                  "owner": "policy-owner",
                  "author": "maker-user",
                                                                        "riskLevel": "LOW",
                  "definition": "allow approval"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/v1/admin/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        String response = createResult.getResponse().getContentAsString();
        String policyId = response.split("\"policyId\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/v1/admin/policies/" + policyId + "/promote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetState": "VALIDATED",
                                  "approvers": ["maker-user"]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.decisionCode").value("POLICY_SEPARATION_OF_DUTIES_VIOLATION"));
    }

    @Test
    void listAuditEventsIncludesPolicyLifecycleEvidence() throws Exception {
        String createBody = """
                {
                  "name": "POL.RETAIL.ACCOUNT.EXPORT.DENY.v1",
                  "effect": "DENY",
                  "owner": "policy-owner",
                  "author": "audit-user",
                                                                        "riskLevel": "LOW",
                  "definition": "deny export"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/v1/admin/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        String response = createResult.getResponse().getContentAsString();
        String policyId = response.split("\"policyId\":\"")[1].split("\"")[0];

        String events = mockMvc.perform(get("/v1/admin/audit-events").param("entityId", policyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("POLICY_DRAFT_CREATED"))
                .andExpect(jsonPath("$[0].decisionCode").value("POLICY_DRAFT_CREATED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(events).contains(policyId);
    }

                @Test
                void approveFailsWhenSimulationCoverageBelowRiskThreshold() throws Exception {
                                MvcResult createResult = mockMvc.perform(post("/v1/admin/policies")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "name": "POL.HIGH.RISK.EXAMPLE.v1",
                                                                                                                                        "effect": "ALLOW",
                                                                                                                                        "owner": "policy-owner",
                                                                                                                                        "author": "maker-user",
                                                                                                                                        "riskLevel": "HIGH",
                                                                                                                                        "definition": "high risk policy"
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isCreated())
                                                                .andReturn();

                                String policyId = createResult.getResponse().getContentAsString().split("\"policyId\":\"")[1].split("\"")[0];

                                mockMvc.perform(post("/v1/admin/policies/" + policyId + "/promote")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "targetState": "VALIDATED",
                                                                                                                                        "approvers": ["checker-user"]
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isOk());

                                mockMvc.perform(post("/v1/admin/policies/" + policyId + "/promote")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "targetState": "APPROVED",
                                                                                                                                        "approvers": ["checker-user", "security-user"],
                                                                                                                                        "approverRoles": ["POLICY_REVIEWER", "SECURITY_GOVERNANCE"],
                                                                                                                                        "simulationCoverage": 94
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isConflict())
                                                                .andExpect(jsonPath("$.decisionCode").value("POLICY_SIMULATION_COVERAGE_INSUFFICIENT"));
                }

                @Test
                void approveFailsWhenHighRiskMissingSecurityGovernanceRole() throws Exception {
                                MvcResult createResult = mockMvc.perform(post("/v1/admin/policies")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "name": "POL.HIGH.RISK.ROLE.v1",
                                                                                                                                        "effect": "ALLOW",
                                                                                                                                        "owner": "policy-owner",
                                                                                                                                        "author": "maker-user",
                                                                                                                                        "riskLevel": "HIGH",
                                                                                                                                        "definition": "high risk role gate"
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isCreated())
                                                                .andReturn();

                                String policyId = createResult.getResponse().getContentAsString().split("\"policyId\":\"")[1].split("\"")[0];

                                mockMvc.perform(post("/v1/admin/policies/" + policyId + "/promote")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "targetState": "VALIDATED",
                                                                                                                                        "approvers": ["checker-user"]
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isOk());

                                mockMvc.perform(post("/v1/admin/policies/" + policyId + "/promote")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "targetState": "APPROVED",
                                                                                                                                        "approvers": ["checker-user", "owner-user"],
                                                                                                                                        "approverRoles": ["POLICY_REVIEWER", "OWNER"],
                                                                                                                                        "simulationCoverage": 97
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isConflict())
                                                                .andExpect(jsonPath("$.decisionCode").value("POLICY_REQUIRED_APPROVER_ROLE_MISSING"));
                }

                @Test
                void activePromotionRequiresRollbackReferenceAndRationale() throws Exception {
                                MvcResult createResult = mockMvc.perform(post("/v1/admin/policies")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "name": "POL.LOW.RISK.ACTIVE.v1",
                                                                                                                                        "effect": "ALLOW",
                                                                                                                                        "owner": "policy-owner",
                                                                                                                                        "author": "maker-user",
                                                                                                                                        "riskLevel": "LOW",
                                                                                                                                        "definition": "active gate policy"
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isCreated())
                                                                .andReturn();

                                String policyId = createResult.getResponse().getContentAsString().split("\"policyId\":\"")[1].split("\"")[0];

                                mockMvc.perform(post("/v1/admin/policies/" + policyId + "/promote")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "targetState": "VALIDATED",
                                                                                                                                        "approvers": ["checker-user"]
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isOk());

                                mockMvc.perform(post("/v1/admin/policies/" + policyId + "/promote")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "targetState": "APPROVED",
                                                                                                                                        "approvers": ["checker-user"],
                                                                                                                                        "simulationCoverage": 85
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isOk());

                                mockMvc.perform(post("/v1/admin/policies/" + policyId + "/promote")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "targetState": "STAGED",
                                                                                                                                        "approvers": ["checker-user"],
                                                                                                                                        "simulationCoverage": 85
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isConflict())
                                                                .andExpect(jsonPath("$.decisionCode").value("POLICY_CHANGE_RATIONALE_REQUIRED"));
                }

                                                                                @Test
                                                                                void verifyDisasterRecoveryContinuityReturnsVerificationEnvelope() throws Exception {
                                                                                                String response = mockMvc.perform(get("/v1/admin/recovery/continuity"))
                                                                                                                .andExpect(status().isOk())
                                                                                                                .andExpect(jsonPath("$.policyRegistryAvailable").isBoolean())
                                                                                                                .andExpect(jsonPath("$.auditStoreAvailable").isBoolean())
                                                                                                                .andExpect(jsonPath("$.policyAuditContinuityIntact").isBoolean())
                                                                                                                .andExpect(jsonPath("$.failoverRehearsalExecuted").value(false))
                                                                                                                .andExpect(jsonPath("$.failoverRehearsalPassed").value(true))
                                                                                                                .andExpect(jsonPath("$.decisionCode").exists())
                                                                                                                .andReturn()
                                                                                                                .getResponse()
                                                                                                                .getContentAsString();

                                                                                                assertThat(response).contains("verifiedAt");
                                                                                }

                @Test
                void verifyDisasterRecoveryContinuityReportsCoverageForActivePolicies() throws Exception {
                                MvcResult createResult = mockMvc.perform(post("/v1/admin/policies")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "name": "POL.LOW.RISK.DR.v1",
                                                                                                                                        "effect": "ALLOW",
                                                                                                                                        "owner": "policy-owner",
                                                                                                                                        "author": "maker-user",
                                                                                                                                        "riskLevel": "LOW",
                                                                                                                                        "definition": "dr continuity policy"
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isCreated())
                                                                .andReturn();

                                String policyId = createResult.getResponse().getContentAsString().split("\"policyId\":\"")[1].split("\"")[0];

                                mockMvc.perform(post("/v1/admin/policies/" + policyId + "/promote")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "targetState": "VALIDATED",
                                                                                                                                        "approvers": ["checker-user"]
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isOk());

                                mockMvc.perform(post("/v1/admin/policies/" + policyId + "/promote")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "targetState": "APPROVED",
                                                                                                                                        "approvers": ["checker-user"],
                                                                                                                                        "simulationCoverage": 85
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isOk());

                                mockMvc.perform(post("/v1/admin/policies/" + policyId + "/promote")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "targetState": "STAGED",
                                                                                                                                        "approvers": ["checker-user"],
                                                                                                                                        "simulationCoverage": 85,
                                                                                                                                        "changeRationale": "staged for dr"
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isOk());

                                mockMvc.perform(post("/v1/admin/policies/" + policyId + "/promote")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "targetState": "ACTIVE",
                                                                                                                                        "approvers": ["checker-user"],
                                                                                                                                        "simulationCoverage": 85,
                                                                                                                                        "changeRationale": "activate for dr",
                                                                                                                                        "rollbackReference": "rbk-001"
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isOk());

                                mockMvc.perform(get("/v1/admin/recovery/continuity"))
                                                .andExpect(status().isOk())
                                                .andExpect(jsonPath("$.activePolicyCount").value(1))
                                                .andExpect(jsonPath("$.activePoliciesWithAuditCoverage").value(1))
                                                .andExpect(jsonPath("$.policyAuditContinuityIntact").value(true))
                                                .andExpect(jsonPath("$.decisionCode").value("DR_CONTINUITY_VERIFIED"));
                }

                @Test
                void verifyDisasterRecoveryContinuityWithRehearsalRunsHealthCheck() throws Exception {
                                mockMvc.perform(get("/v1/admin/recovery/continuity").param("rehearsal", "true"))
                                                .andExpect(status().isOk())
                                                .andExpect(jsonPath("$.failoverRehearsalExecuted").value(true))
                                                .andExpect(jsonPath("$.failoverRehearsalPassed").value(true));
                }
}
