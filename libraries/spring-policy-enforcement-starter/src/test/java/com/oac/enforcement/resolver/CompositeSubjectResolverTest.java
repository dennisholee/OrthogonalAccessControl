package com.oac.enforcement.resolver;

import com.oac.enforcement.OacEntitlementConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for {@link CompositeSubjectResolver}.
 * Verifies chain ordering, first-non-null semantics, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class CompositeSubjectResolverTest {

    @Mock
    private HttpServletRequest request;

    /**
     * Helper to create a SubjectResolver that returns a fixed value with a given order.
     */
    private static SubjectResolver resolver(String value, int order) {
        return new SubjectResolver() {
            @Override
            public String resolve(HttpServletRequest r, OacEntitlementConfig c) {
                return value;
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }

    /** Resolver that returns null. */
    private static SubjectResolver nullResolver(int order) {
        return new SubjectResolver() {
            @Override
            public String resolve(HttpServletRequest r, OacEntitlementConfig c) {
                return null;
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }

    /** Resolver that returns empty/blank strings. */
    private static SubjectResolver blankResolver(String blank, int order) {
        return new SubjectResolver() {
            @Override
            public String resolve(HttpServletRequest r, OacEntitlementConfig c) {
                return blank;
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }

    @Test
    void shouldReturnFirstNonNullResult() {
        CompositeSubjectResolver composite = new CompositeSubjectResolver(
                List.of(nullResolver(0), resolver("alice", 1), resolver("bob", 2)));
        assertEquals("alice", composite.resolve(request, null));
    }

    @Test
    void shouldReturnNullWhenAllReturnNull() {
        CompositeSubjectResolver composite = new CompositeSubjectResolver(
                List.of(nullResolver(0), nullResolver(1)));
        assertNull(composite.resolve(request, null));
    }

    @Test
    void shouldSkipBlankResults() {
        CompositeSubjectResolver composite = new CompositeSubjectResolver(
                List.of(blankResolver("", 0), blankResolver("  ", 1), resolver("alice", 2)));
        assertEquals("alice", composite.resolve(request, null));
    }

    @Test
    void shouldReturnFirstNonNullEvenIfLaterHasNull() {
        CompositeSubjectResolver composite = new CompositeSubjectResolver(
                List.of(resolver("admin", 0), nullResolver(1)));
        assertEquals("admin", composite.resolve(request, null));
    }

    @Test
    void shouldRespectOrderAnnotation() {
        SubjectResolver low = new SubjectResolver() {
            public String resolve(HttpServletRequest r, OacEntitlementConfig c) { return "low"; }
            public int getOrder() { return 30; }
        };
        SubjectResolver high = new SubjectResolver() {
            public String resolve(HttpServletRequest r, OacEntitlementConfig c) { return "high"; }
            public int getOrder() { return 10; }
        };

        CompositeSubjectResolver composite = new CompositeSubjectResolver(List.of(low, high));
        assertEquals("high", composite.resolve(request, null));
    }

    @Test
    void shouldHandleEmptyResolverList() {
        CompositeSubjectResolver composite = new CompositeSubjectResolver(List.of());
        assertNull(composite.resolve(request, null));
    }

    @Test
    void shouldHaveLowestPrecedenceOrder() {
        SubjectResolver only = resolver("alice", 0);
        CompositeSubjectResolver composite = new CompositeSubjectResolver(List.of(only));
        assertTrue(composite.getOrder() >= Ordered.LOWEST_PRECEDENCE);
    }

    @Test
    void shouldPassConfigToEachResolver() {
        OacEntitlementConfig config = new OacEntitlementConfig(
                "read", "order", "orderId", false, null, null);

        SubjectResolver capturingResolver = new SubjectResolver() {
            OacEntitlementConfig captured;

            @Override
            public String resolve(HttpServletRequest r, OacEntitlementConfig c) {
                this.captured = c;
                return c != null ? c.action() : null;
            }

            @Override
            public int getOrder() {
                return 0;
            }
        };

        CompositeSubjectResolver composite = new CompositeSubjectResolver(List.of(capturingResolver));
        assertEquals("read", composite.resolve(request, config));
    }
}