package com.oac.decision.application.port.in;

import com.oac.decision.model.AuditEventRecord;
import com.oac.decision.model.CreatePolicyRequest;
import com.oac.decision.model.DisasterRecoveryStatusResponse;
import com.oac.decision.model.PolicyResponse;
import com.oac.decision.model.PromotePolicyRequest;

import java.util.List;

public interface PolicyAdministrationUseCase {

    PolicyResponse createPolicyDraft(CreatePolicyRequest request);

    PolicyResponse promotePolicy(String policyId, PromotePolicyRequest request);

    List<AuditEventRecord> listAuditEvents(String entityId);

    DisasterRecoveryStatusResponse verifyDisasterRecoveryContinuity(boolean runFailoverRehearsal);
}