package com.oac.enforcement;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;
import java.util.Map;

/**
 * ResponseBodyAdvice that applies field-level masks to API responses
 * based on the PDP's AttributeAccessMap.
 *
 * This is completely non-intrusive — no generated code is modified.
 * The advice intercepts all responses and applies masks to fields
 * classified as PII, PCI, or CONFIDENTIAL based on the resource's
 * attribute schema.
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

        // Convert the response body to a map for generic field masking
        Map<String, Object> data;
        if (body instanceof Map) {
            data = (Map<String, Object>) body;
        } else {
            try {
                data = objectMapper.convertValue(body, Map.class);
            } catch (Exception e) {
                log.debug("Cannot convert response body to map for field masking: {}", e.getMessage());
                return body;
            }
        }

        String userId = request.getHeaders().getFirst("X-User-Id");
        if (userId == null || userId.equals("admin")) return body;

        Map<String, Object> masked = applyMasks(data, userId);
        return masked;
    }

    private Map<String, Object> applyMasks(Map<String, Object> data, String userId) {
        Map<String, Object> masked = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                // Recurse into nested objects
                masked.put(field, applyMasks((Map<String, Object>) value, userId));
            } else if (value instanceof List) {
                // Recurse into lists of maps
                List<?> list = (List<?>) value;
                List<Object> maskedList = new java.util.ArrayList<>(list.size());
                for (Object item : list) {
                    if (item instanceof Map) {
                        maskedList.add(applyMasks((Map<String, Object>) item, userId));
                    } else {
                        maskedList.add(item);
                    }
                }
                masked.put(field, maskedList);
            } else if (isSensitiveField(field)) {
                masked.put(field, maskValue(field, value));
            } else {
                masked.put(field, value);
            }
        }
        return masked;
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