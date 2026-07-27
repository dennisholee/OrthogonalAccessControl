package com.oac.enforcement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link RestClientDecisionClient}.
 * Verifies HTTP construction, response parsing, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class RestClientDecisionClientTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @Captor
    private ArgumentCaptor<Map<String, String>> bodyCaptor;

    private RestClientDecisionClient client;

    @BeforeEach
    void setUp() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Object.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);

        client = new RestClientDecisionClient(restClient, "http://pdp:8080");
    }

    @Test
    void shouldReturnTrueWhenPdpRespondsAllow() {
        when(responseSpec.body(any(Class.class))).thenReturn(Map.of("decision", "ALLOW"));

        assertTrue(client.checkPermission("alice", "read", "order/ORD-001"));
    }

    @Test
    void shouldReturnFalseWhenPdpRespondsDeny() {
        when(responseSpec.body(any(Class.class))).thenReturn(Map.of("decision", "DENY"));

        assertFalse(client.checkPermission("alice", "read", "order/ORD-001"));
    }

    @Test
    void shouldReturnFalseWhenPdpReturnsNullResponse() {
        when(responseSpec.body(any(Class.class))).thenReturn(null);

        assertFalse(client.checkPermission("unknown", "delete", "order/ORD-999"));
    }

    @Test
    void shouldReturnFalseWhenPdpRespondsWithUnknownDecision() {
        when(responseSpec.body(any(Class.class))).thenReturn(Map.of("decision", "UNKNOWN"));

        assertFalse(client.checkPermission("user", "read", "order/1"));
    }

    @Test
    void shouldReturnFalseWhenPdpThrowsException() {
        when(responseSpec.body(any(Class.class))).thenThrow(new RuntimeException("Connection refused"));

        assertFalse(client.checkPermission("user", "read", "order/1"));
    }

    @Test
    void shouldTrimTrailingSlashFromPdpUrl() {
        when(responseSpec.body(any(Class.class))).thenReturn(Map.of("decision", "ALLOW"));

        client = new RestClientDecisionClient(restClient, "http://pdp:8080/");
        assertTrue(client.checkPermission("alice", "read", "order/ORD-001"));
        verify(requestBodyUriSpec).uri("http://pdp:8080/v1/decisions/check-permission");
    }

    @Test
    void shouldSendCorrectRequestBody() {
        when(responseSpec.body(any(Class.class))).thenReturn(Map.of("decision", "DENY"));

        client.checkPermission("bob", "approve", "order/ORD-002");

        verify(requestBodySpec).body(bodyCaptor.capture());
        Map<String, String> sentBody = bodyCaptor.getValue();
        assertEquals("bob", sentBody.get("subjectId"));
        assertEquals("approve", sentBody.get("action"));
        assertEquals("order/ORD-002", sentBody.get("resourceId"));
    }

    @Test
    void shouldReturnTrueOnCaseInsensitiveAllowCheck() {
        when(responseSpec.body(any(Class.class))).thenReturn(Map.of("decision", "allow"));

        assertTrue(client.checkPermission("alice", "read", "order/1"));
    }
}
