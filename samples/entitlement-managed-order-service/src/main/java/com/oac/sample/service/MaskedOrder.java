package com.oac.sample.service;

import com.oac.sample.model.Order;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Wrapper around Order that applies field-level masks based on the
 * decision engine's AttributeAccessMap.
 */
public class MaskedOrder {

    private final Map<String, Object> safeFields = new LinkedHashMap<>();

    /**
     * Creates a MaskedOrder by applying the provided field masks to the original Order.
     *
     * @param order   the original order data
     * @param masks   map of field path -> access level ("MASK", "NONE", "READ")
     */
    public MaskedOrder(Order order, Map<String, Object> masks) {
        safeFields.put("id", order.getId());
        safeFields.put("product", order.getProduct());
        safeFields.put("quantity", order.getQuantity());
        safeFields.put("total", order.getTotal());
        safeFields.put("status", order.getStatus());
        safeFields.put("ownerId", order.getOwnerId());

        // Apply masks to customer PII fields
        safeFields.put("customerName", applyMask("customer.name", order.getCustomerName(), masks));
        safeFields.put("customerEmail", applyMask("customer.email", order.getCustomerEmail(), masks));
        safeFields.put("customerSsn", applyMask("customer.ssn", order.getCustomerSsn(), masks));
        safeFields.put("customerPhone", applyMask("customer.phone", order.getCustomerPhone(), masks));
    }

    public Map<String, Object> toMap() {
        return safeFields;
    }

    private Object applyMask(String fieldPath, String rawValue, Map<String, Object> masks) {
        if (rawValue == null) return null;

        // Check field-level ACL first
        String level = masks != null ? (String) masks.get(fieldPath) : null;
        if (level == null) {
            // Check wildcard pattern (e.g., customer.*)
            String wildcard = fieldPath.substring(0, fieldPath.lastIndexOf('.') + 1) + "*";
            level = masks != null ? (String) masks.get(wildcard) : null;
        }
        if (level == null) {
            // Default: READ
            return rawValue;
        }

        return switch (level) {
            case "READ" -> rawValue;
            case "MASK" -> maskValue(rawValue);
            case "NONE" -> null;
            default -> rawValue;
        };
    }

    private String maskValue(String value) {
        if (value == null || value.length() <= 2) return "***";
        if (value.contains("@")) {
            // Email: j***@acme.com
            return value.charAt(0) + "***@" + value.substring(value.indexOf('@') + 1);
        }
        // Other: show first char + asterisks
        return value.charAt(0) + "***";
    }
}