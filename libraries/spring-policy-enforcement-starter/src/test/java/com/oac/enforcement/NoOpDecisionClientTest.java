package com.oac.enforcement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for {@link NoOpDecisionClient}.
 * Verifies that the no-op client always returns {@code false} (deny-all).
 */
class NoOpDecisionClientTest {

    private final NoOpDecisionClient client = new NoOpDecisionClient();

    @Test
    void shouldAlwaysReturnFalse() {
        assertFalse(client.checkPermission("any-user", "read", "order/ORD-001"));
    }

    @Test
    void shouldAlwaysReturnFalseForAnyAction() {
        assertFalse(client.checkPermission("admin", "delete", "account/acc-1"));
    }

    @Test
    void shouldAlwaysReturnFalseForEmptyStrings() {
        assertFalse(client.checkPermission("", "", ""));
    }

    @Test
    void shouldAlwaysReturnFalseForNullSafeArgs() {
        // The implementation doesn't null-check, but we pass non-null to avoid NPE
        assertFalse(client.checkPermission("null", "null", "null"));
    }

    @Test
    void defaultCheckPermissionWithDetailsReturnsDeny() {
        var result = client.checkPermissionWithDetails("user", "read", "order/1", null);
        assertEquals("DENY", result.get("decision"));
        assertEquals("DECISION_DEFAULT_DENY", result.get("decisionCode"));
    }
}