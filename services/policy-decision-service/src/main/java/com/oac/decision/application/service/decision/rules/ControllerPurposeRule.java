package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.port.out.ControllerPurposeRegistryPort;
import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;

import java.util.Optional;

/**
 * ControllerPurposeRule — validates the requested processing purpose against the
 * data controller's registered purpose scope (Section 4.17).
 * <p>
 * When the request declares a {@code purpose} in boundaryContext, the PDP MUST verify
 * that the requesting tenant (as data controller) has registered that purpose. A purpose
 * that is not registered — or exceeds the controller's processing instructions — is
 * rejected with {@code DECISION_PURPOSE_NOT_AUTHORISED_FOR_CONTROLLER}.
 * <p>
 * The rule only fires when the request declares a purpose (backward-compatible with
 * non-CDP requests that omit purpose).
 */
public class ControllerPurposeRule implements DecisionRule {

    private final ControllerPurposeRegistryPort controllerPurposeRegistry;

    public ControllerPurposeRule(ControllerPurposeRegistryPort controllerPurposeRegistry) {
        this.controllerPurposeRegistry = controllerPurposeRegistry;
    }

    @Override
    public Optional<DecisionOutcome> evaluate(DecisionContext context) {
        var bc = context.request().boundaryContext();
        if (bc == null || bc.purpose() == null || bc.purpose().isBlank()) {
            return Optional.empty();
        }

        String tenant = bc.tenant();
        String purpose = bc.purpose();

        if (!controllerPurposeRegistry.isPurposeAuthorized(tenant, purpose)) {
            return Optional.of(new DecisionOutcome(
                    "DENY",
                    "DECISION_PURPOSE_NOT_AUTHORISED_FOR_CONTROLLER",
                    "evidence://decision/purpose-not-authorized/" + tenant + "/" + purpose
            ));
        }

        return Optional.empty();
    }
}
