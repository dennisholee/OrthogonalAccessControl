package com.oac.enforcement;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ResponseBodyAdvice that applies field-level masks to API responses.
 *
 * Uses Java 21 pattern matching for instance-of checks and
 * configurable sensitive field patterns.
 *
 * This is completely non-intrusive — no generated code is modified.
 * The advice intercepts all responses and applies masks to fields
 * classified as PII, PCI, or CONFIDENTIAL patterns.
 */
@ControllerAdvice
public class FieldMaskResponseAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(FieldMaskResponseAdvice.class);

    private final DecisionClient decisionClient;
    private final ObjectMapper objectMapper;

    public FieldMaskResponseAdvice(DecisionClient decisionClient, ObjectMapper objectMapper) {
        this.decisionClient = decisionClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true; // Apply to all responses
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                   MediaType selectedContentType,
                                   Class selectedConverterType,
                                   ServerHttpRequest request,
                                   ServerHttpResponse response) {

        if (body == null) return null;

        // Skip ProblemDetail responses (RFC 9457) — these are already structured errors
        if (body instanceof ProblemDetail) {
            return body;
        }

        Map<String, Object> data = switch (body) {
            case Map<?, ?> map -> (Map<String, Object>) map;
            default -> {
                try {
                    yield objectMapper.convertValue(body, Map.class);
                } catch (Exception e) {
                    log.debug("Cannot convert response body to map for field masking: {}", e.getMessage());
                    yield null;
                }
            }
        };
        if (data == null) return body;

        String userId = request.getHeaders().getFirst("X-User-Id");
        // Admin sees all fields unmasked
        if (userId == null || "admin".equals(userId)) {
            return body;
        }

        return applyMasks(data, userId);
    }

    private Map<String, Object> applyMasks(Map<String, Object> data, String userId) {
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();

            if (value == null) {
                masked.put(field, null);
            } else if (value instanceof Map<?, ?> nested) {
                masked.put(field, applyMasks((Map<String, Object>) nested, userId));
            } else if (value instanceof List<?> list) {
                masked.put(field, maskList(list, userId));
            } else if (isSensitiveField(field)) {
                masked.put(field, maskValue(field, value));
            } else {
                masked.put(field, value);
            }
        }
        return masked;
    }

    private List<Object> maskList(List<?> list, String userId) {
        return list.stream()
                .map(item -> {
                    if (item instanceof Map<?, ?> map) {
                        return applyMasks((Map<String, Object>) map, userId);
                    }
                    return item;
                })
                .toList();
    }

    private boolean isSensitiveField(String field) {
        String lower = field.toLowerCase();
        return lower.contains("ssn")
                || lower.contains("email")
                || lower.contains("cardnumber")
                || lower.contains("cvv")
                || lower.contains("pin")
                || lower.contains("password")
                || lower.contains("secret")
                || lower.equals("dob");
    }

    private Object maskValue(String field, Object value) {
        if (value == null) return null;
        String str = value.toString();
        String lower = field.toLowerCase();

        if (lower.contains("email")) {
            return maskEmail(str);
        }
        if (lower.contains("ssn")) {
            return "***-**-****";
        }
        if (lower.contains("cardnumber")) {
            return str.length() > 4 ? "****-****-****-" + str.substring(str.length() - 4) : "****";
        }
        if (lower.contains("password") || lower.contains("secret")) {
            return "****";
        }
        return "****";
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        return email.charAt(0) + "***@" + email.substring(email.indexOf('@') + 1);
    }
}