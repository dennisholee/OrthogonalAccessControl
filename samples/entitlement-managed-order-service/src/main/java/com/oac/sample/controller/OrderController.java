package com.oac.sample.controller;

import com.oac.sample.model.Order;
import com.oac.sample.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API controller demonstrating entitlement enforcement.
 *
 * Headers:
 *   X-User-Id:      User identifier (e.g., "alice", "bob", "attacker")
 *   X-User-Role:    User role (e.g., "csr", "senior-analyst", "auditor")
 *   X-Service-Id:   Service identifier for workload auth (e.g., "reporting-service")
 *   X-Service-Type: "workload" for service-to-service calls
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * GET /api/orders/{orderId} — Read a single order with entitlement enforcement.
     *
     * Demo scenarios:
     *   alice  → 200 OK (field-masked PII)
     *   attacker → 403 Forbidden (explicit deny)
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable("orderId") String orderId,
                                      @RequestHeader("X-User-Id") String userId) {
        try {
            Object result = orderService.getOrder(orderId, userId);
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/orders — List all orders accessible to the user.
     *
     * Demo scenario:
     *   auditor → 200 OK with all orders but PII redacted
     */
    @GetMapping
    public ResponseEntity<?> getAllOrders(@RequestHeader("X-User-Id") String userId) {
        try {
            List<?> orders = orderService.getAllOrders(userId);
            return ResponseEntity.ok(orders);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/orders/{orderId}/approve — Approve an order via ReBAC.
     *
     * Demo scenario:
     *   bob (manager of alice) → 200 OK
     *   eve (no relationship) → 403 Forbidden
     */
    @PostMapping("/{orderId}/approve")
    public ResponseEntity<?> approveOrder(@PathVariable("orderId") String orderId,
                                          @RequestHeader("X-User-Id") String userId) {
        try {
            Order approved = orderService.approveOrder(orderId, userId);
            return ResponseEntity.ok(Map.of(
                    "orderId", approved.getId(),
                    "status", approved.getStatus()
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/orders/aggregate — Get aggregated order data for services.
     *
     * Demo scenario:
     *   reporting-service (workload) → 200 OK with all order data
     */
    @GetMapping("/aggregate")
    public ResponseEntity<?> getAggregate(@RequestHeader(value = "X-Service-Id", defaultValue = "reporting-service") String serviceId,
                                          @RequestHeader(value = "X-Service-Type", defaultValue = "workload") String serviceType) {
        try {
            List<Order> aggregate = orderService.getAggregate(serviceId);
            return ResponseEntity.ok(Map.of(
                    "service", serviceId,
                    "totalOrders", aggregate.size(),
                    "totalRevenue", aggregate.stream().mapToDouble(Order::getTotal).sum(),
                    "orders", aggregate.stream().map(o -> Map.of(
                            "orderId", o.getId(),
                            "product", o.getProduct(),
                            "quantity", o.getQuantity(),
                            "total", o.getTotal()
                    )).toList()
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/orders/seed — Seed sample data for demo.
     */
    @PostMapping("/seed")
    public ResponseEntity<?> seedData() {
        orderService.seedSampleData();
        return ResponseEntity.ok(Map.of("message", "Sample data seeded"));
    }
}