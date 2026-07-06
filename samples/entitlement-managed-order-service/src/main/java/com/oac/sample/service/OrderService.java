package com.oac.sample.service;

import com.oac.enforcement.DecisionClient;
import com.oac.sample.model.Order;
import com.oac.sample.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Order business service that enforces entitlements.
 * Every method checks permissions via the DecisionClient before
 * returning data. PII is dynamically masked based on the decision.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final DecisionClient decisionClient;

    public OrderService(OrderRepository orderRepository, DecisionClient decisionClient) {
        this.orderRepository = orderRepository;
        this.decisionClient = decisionClient;
    }

    /**
     * Get order by ID — enforces READ permission.
     * If PDP returns field masks, PII is masked in the response.
     */
    public Object getOrder(String orderId, String userId) {
        // Step 1: Check permission with PDP
        if (!decisionClient.checkPermission(userId, "READ", orderId)) {
            log.warn("DENIED: {} READ {}", userId, orderId);
            throw new SecurityException("Access denied: user " + userId + " cannot READ order " + orderId);
        }

        // Step 2: Fetch data from MongoDB
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        // Step 3: Get detailed decision for field masks
        Map<String, Object> masks = getFieldMasks(userId, orderId);

        // Step 4: Apply masks
        log.info("ALLOWED: {} READ {} with masks: {}", userId, orderId, masks);
        return new MaskedOrder(order, masks).toMap();
    }

    /**
     * Get all orders — enforced per-order via READ permission.
     */
    public List<?> getAllOrders(String userId) {
        List<Order> allOrders = orderRepository.findAll();
        return allOrders.stream()
                .filter(order -> decisionClient.checkPermission(userId, "READ", order.getId()))
                .map(order -> {
                    Map<String, Object> masks = getFieldMasks(userId, order.getId());
                    return new MaskedOrder(order, masks).toMap();
                })
                .toList();
    }

    /**
     * Approve an order via ReBAC — checks APPROVE permission.
     */
    public Order approveOrder(String orderId, String userId) {
        if (!decisionClient.checkPermission(userId, "APPROVE", orderId)) {
            log.warn("DENIED: {} APPROVE {}", userId, orderId);
            throw new SecurityException("Access denied: user " + userId + " cannot APPROVE order " + orderId);
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.setStatus("APPROVED");
        orderRepository.save(order);
        log.info("ALLOWED: {} APPROVED {}", userId, orderId);
        return order;
    }

    /**
     * Get aggregated order data — used by reporting services.
     */
    public List<Order> getAggregate(String serviceId) {
        if (!decisionClient.checkPermission(serviceId, "READ_AGGREGATE", "order/aggregate")) {
            log.warn("DENIED: {} READ_AGGREGATE order/aggregate", serviceId);
            throw new SecurityException("Access denied: service " + serviceId + " cannot READ_AGGREGATE orders");
        }

        List<Order> allOrders = orderRepository.findAll();
        log.info("ALLOWED: {} READ_AGGREGATE — {} orders", serviceId, allOrders.size());
        return allOrders;
    }

    /**
     * Seeds sample orders into MongoDB for demo purposes.
     */
    public void seedSampleData() {
        if (orderRepository.count() > 0) {
            log.info("Sample data already seeded");
            return;
        }

        orderRepository.save(new Order("ORD-001", "Alice Johnson", "alice@acme.com",
                "123-45-6789", "Widget A", 10, 299.99, "alice"));
        orderRepository.save(new Order("ORD-002", "Bob Smith", "bob@acme.com",
                "987-65-4321", "Gadget B", 5, 149.95, "bob"));
        orderRepository.save(new Order("ORD-003", "Carol Davis", "carol@acme.com",
                "456-78-9012", "Service C", 1, 999.00, "carol"));
        orderRepository.save(new Order("ORD-004", "Dave Brown", "dave@acme.com",
                "321-54-9876", "Product D", 20, 49.99, "dave"));
        log.info("Seeded 4 sample orders");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getFieldMasks(String userId, String resourceId) {
        Map<String, Object> decision = decisionClient.checkPermissionWithDetails(userId, "READ", resourceId, null);
        Object accessMap = decision.get("attributeAccessMap");
        if (accessMap instanceof Map<?, ?> access) {
            Map<String, Object> flat = new java.util.LinkedHashMap<>();

            // Case 1: REST format — nested { fieldAccess: { "customer.email": "MASK", ... } }
            Object fieldAccess = access.get("fieldAccess");
            if (fieldAccess instanceof Map<?, ?> fields) {
                for (var entry : fields.entrySet()) {
                    flat.put(entry.getKey().toString(), entry.getValue().toString());
                }
                if (!flat.isEmpty()) return flat;
            }

            // Case 2: DirectDecisionClient format — flat { "customer.email": "MASK", ... }
            // where keys are field paths and values are access level name strings.
            boolean looksFlat = access.keySet().stream().anyMatch(
                    k -> k instanceof String s && (s.contains(".") || s.contains("*")));
            if (looksFlat) {
                for (var entry : access.entrySet()) {
                    if (entry.getValue() != null) {
                        flat.put(entry.getKey().toString(), entry.getValue().toString());
                    }
                }
                return flat;
            }
        }
        return Map.of();
    }
}