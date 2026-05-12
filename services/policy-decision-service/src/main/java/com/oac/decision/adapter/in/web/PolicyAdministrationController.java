package com.oac.decision.adapter.in.web;

import com.oac.decision.application.port.in.PolicyAdministrationUseCase;
import com.oac.decision.model.AuditEventRecord;
import com.oac.decision.model.CreatePolicyRequest;
import com.oac.decision.model.DisasterRecoveryStatusResponse;
import com.oac.decision.model.PolicyResponse;
import com.oac.decision.model.PromotePolicyRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/v1/admin")
public class PolicyAdministrationController {

    private final PolicyAdministrationUseCase policyAdministrationUseCase;

    public PolicyAdministrationController(PolicyAdministrationUseCase policyAdministrationUseCase) {
        this.policyAdministrationUseCase = policyAdministrationUseCase;
    }

    @PostMapping("/policies")
    public ResponseEntity<PolicyResponse> createPolicyDraft(@Valid @RequestBody CreatePolicyRequest request) {
        return ResponseEntity.status(201).body(policyAdministrationUseCase.createPolicyDraft(request));
    }

    @PostMapping("/policies/{policyId}/promote")
    public ResponseEntity<PolicyResponse> promotePolicy(
            @PathVariable("policyId") String policyId,
            @Valid @RequestBody PromotePolicyRequest request
    ) {
        return ResponseEntity.ok(policyAdministrationUseCase.promotePolicy(policyId, request));
    }

    @GetMapping("/audit-events")
    public ResponseEntity<List<AuditEventRecord>> listAuditEvents(
            @RequestParam(name = "entityId", required = false) String entityId
    ) {
        return ResponseEntity.ok(policyAdministrationUseCase.listAuditEvents(entityId));
    }

    @GetMapping("/recovery/continuity")
    public ResponseEntity<DisasterRecoveryStatusResponse> verifyDisasterRecoveryContinuity(
            @RequestParam(name = "rehearsal", defaultValue = "false") boolean rehearsal
    ) {
        return ResponseEntity.ok(policyAdministrationUseCase.verifyDisasterRecoveryContinuity(rehearsal));
    }
}