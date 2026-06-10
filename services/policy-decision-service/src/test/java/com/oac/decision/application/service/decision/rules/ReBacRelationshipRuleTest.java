package com.oac.decision.application.service.decision.rules;

import com.oac.decision.application.port.out.RelationshipGraphPort;
import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.application.service.decision.DecisionOutcome;
import com.oac.decision.application.service.decision.DecisionRule;
import com.oac.decision.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReBacRelationshipRuleTest {

    @Test
    void shouldSkipWhenNoReBACPolicyMatched() {
        RelationshipGraphPort mock = mock(RelationshipGraphPort.class);
        DecisionRule rule = new ReBacRelationshipRule(mock);
        var context = createContext(mock, List.of("POL.RBAC.ACCOUNT.READ.ALLOW.v1"), false);

        Optional<DecisionOutcome> result = rule.evaluate(context);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldDenyWhenRelationshipNotFound() {
        RelationshipGraphPort mock = mock(RelationshipGraphPort.class);
        when(mock.getMaxTraversalDepth()).thenReturn(3);
        when(mock.hasRelationship(anyString(), anyString(), any(), anyInt())).thenReturn(false);
        DecisionRule rule = new ReBacRelationshipRule(mock);
        var context = createContext(mock, List.of("POL.REBAC.ACCOUNT.RELATIONSHIP.READ.ALLOW.v1"), false);

        Optional<DecisionOutcome> result = rule.evaluate(context);

        assertTrue(result.isPresent());
        assertEquals("DENY", result.get().decision());
        assertEquals("DECISION_REBAC_NO_RELATIONSHIP", result.get().decisionCode());
    }

    @Test
    void shouldAllowWhenRelationshipFound() {
        RelationshipGraphPort mock = mock(RelationshipGraphPort.class);
        when(mock.getMaxTraversalDepth()).thenReturn(3);
        when(mock.hasRelationship(anyString(), anyString(), any(), anyInt())).thenReturn(true);
        DecisionRule rule = new ReBacRelationshipRule(mock);
        var context = createContext(mock, List.of("POL.REBAC.ACCOUNT.RELATIONSHIP.READ.ALLOW.v1"), false);

        Optional<DecisionOutcome> result = rule.evaluate(context);

        assertTrue(result.isPresent());
        assertEquals("ALLOW", result.get().decision());
        assertEquals("DECISION_REBAC_RELATIONSHIP_ALLOW", result.get().decisionCode());
    }

    @Test
    void shouldCheckRelationshipWithCorrectParameters() {
        RelationshipGraphPort mock = mock(RelationshipGraphPort.class);
        when(mock.getMaxTraversalDepth()).thenReturn(3);
        when(mock.hasRelationship(anyString(), anyString(), any(), anyInt())).thenReturn(true);
        DecisionRule rule = new ReBacRelationshipRule(mock);
        var context = createContext(mock, List.of("POL.REBAC.ACCOUNT.RELATIONSHIP.READ.ALLOW.v1"), false);

        rule.evaluate(context);

        verify(mock).hasRelationship("user-1", "acc-123", null, 3);
    }

    private DecisionContext createContext(RelationshipGraphPort mock, List<String> matchedPolicies, boolean hasAllow) {
        var subject = new SubjectRef("human", "user-1");
        var resource = new ResourceRef("account", "acc-123");
        var boundary = new BoundaryContext("tenant-a", "us", "retail", "cards", "staff");
        var request = new CheckPermissionRequest(
                subject, "read", resource, boundary,
                Map.of("relationship", "owner"), null, "req-1", null, null, null
        );
        return new DecisionContext(request, matchedPolicies, Map.of("clientIp", "10.0.0.1"), hasAllow);
    }
}