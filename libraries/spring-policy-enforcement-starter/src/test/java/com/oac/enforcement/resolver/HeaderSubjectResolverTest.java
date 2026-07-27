package com.oac.enforcement.resolver;

import com.oac.enforcement.OacEntitlementConfig;
import com.oac.enforcement.OacEntitlementProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link HeaderSubjectResolver}.
 */
@ExtendWith(MockitoExtension.class)
class HeaderSubjectResolverTest {

    @Mock
    private HttpServletRequest request;

    private HeaderSubjectResolver resolver;
    private OacEntitlementProperties.Identity identity;

    @BeforeEach
    void setUp() {
        identity = new OacEntitlementProperties.Identity();
        identity.setUserHeader("X-User-Id");
        identity.setServiceHeader("X-Service-Id");
        resolver = new HeaderSubjectResolver(identity);
    }

    @Test
    void shouldExtractFromUserHeader() {
        when(request.getHeader("X-User-Id")).thenReturn("alice");

        String result = resolver.resolve(request, null);
        assertEquals("alice", result);
    }

    @Test
    void shouldFallbackToServiceHeader() {
        when(request.getHeader("X-User-Id")).thenReturn(null);
        when(request.getHeader("X-Service-Id")).thenReturn("reporting-service");

        String result = resolver.resolve(request, null);
        assertEquals("reporting-service", result);
    }

    @Test
    void shouldReturnNullWhenBothHeadersAbsent() {
        when(request.getHeader("X-User-Id")).thenReturn(null);
        when(request.getHeader("X-Service-Id")).thenReturn(null);

        String result = resolver.resolve(request, null);
        assertNull(result);
    }

    @Test
    void shouldSkipBlankUserHeaderAndFallback() {
        when(request.getHeader("X-User-Id")).thenReturn("   ");
        when(request.getHeader("X-Service-Id")).thenReturn("reporting-service");

        String result = resolver.resolve(request, null);
        assertEquals("reporting-service", result);
    }

    @Test
    void shouldUseSubjectIdHeaderOverrideFromConfig() {
        OacEntitlementConfig config = new OacEntitlementConfig(
                "read", "order", "orderId", false, null, "X-Custom-Id");

        when(request.getHeader("X-Custom-Id")).thenReturn("custom-user");

        String result = resolver.resolve(request, config);
        assertEquals("custom-user", result);
    }

    @Test
    void shouldFallbackToUserHeaderWhenSubjectIdHeaderIsBlank() {
        OacEntitlementConfig config = new OacEntitlementConfig(
                "read", "order", "orderId", false, null, "X-Missing-Header");

        when(request.getHeader("X-Missing-Header")).thenReturn(null);
        when(request.getHeader("X-User-Id")).thenReturn("alice");

        String result = resolver.resolve(request, config);
        assertEquals("alice", result);
    }

    @Test
    void shouldReturnNullWhenAllSourcesBlank() {
        when(request.getHeader("X-User-Id")).thenReturn("");
        when(request.getHeader("X-Service-Id")).thenReturn("");

        String result = resolver.resolve(request, null);
        assertNull(result);
    }
}