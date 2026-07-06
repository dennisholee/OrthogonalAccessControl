package com.oac.example.controller;

import com.oac.example.api.generated.OrdersApi;
import com.oac.example.api.generated.model.OrderResponse;
import com.oac.example.api.generated.model.AggregateResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Implementation of the generated OrdersApi interface.
 *
 * This class has ZERO OAC imports, ZERO entitlement annotations.
 *
 * Entitlements are enforced entirely by:
 * 1. OacEnforcementInterceptor — checks PDP before this controller runs
 * 2. FieldMaskResponseAdvice — applies field masks to the response
 *
 * This controller is completely unaware of the entitlement layer.
 * Generated code is never modified.
 */
@RestController
public class OrderControllerImpl implements OrdersApi {

    // In-memory sample data
    private final List<Map<String, Object>> orders = new ArrayList<>();

    public OrderControllerImpl() {
        seedOrders();
    }

    @Override
    public ResponseEntity<OrderResponse> getOrder(String orderId) {
        return findOrder(orderId)
                .map(data -> ResponseEntity.ok(toOrderResponse(data)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<List<OrderResponse>> listOrders() {
        List<OrderResponse> result = orders.stream()
                .map(this::toOrderResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<OrderResponse> approveOrder(String orderId) {
        Optional<Map<String, Object>> opt = findOrder(orderId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Map<String, Object> order = opt.get();
        order.put("status", "APPROVED");
        return ResponseEntity.ok(toOrderResponse(order));
    }

    @Override
    public ResponseEntity<AggregateResponse> getAggregate() {
        double totalRevenue = orders.stream()
                .mapToDouble(o -> ((Number) o.get("total")).doubleValue())
                .sum();
        AggregateResponse response = new AggregateResponse();
        response.setTotalOrders(orders.size());
        response.setTotalRevenue(totalRevenue);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Map<String, Object>> seedSampleData() {
        seedOrders();
        return ResponseEntity.ok(Map.of("message", "Sample data seeded"));
    }

    // ── Helper methods ──

    private Optional<Map<String, Object>> findOrder(String id) {
        return orders.stream()
                .filter(o -> id.equals(o.get("id")))
                .findFirst();
    }

    private OrderResponse toOrderResponse(Map<String, Object> data) {
        OrderResponse resp = new OrderResponse();
        resp.setId((String) data.get("id"));
        resp.setProduct((String) data.get("product"));
        resp.setQuantity((Integer) data.get("quantity"));
        resp.setTotal((Double) data.get("total"));
        resp.setStatus((String) data.get("status"));
        resp.setCustomerName((String) data.get("customerName"));
        resp.setCustomerEmail((String) data.get("customerEmail"));
        resp.setCustomerSsn((String) data.get("customerSsn"));
        return resp;
    }

    private void seedOrders() {
        if (!orders.isEmpty()) return;
        orders.add(order("ORD-001", "Widget A", 10, 299.99,
                "Alice Johnson", "alice@acme.com", "123-45-6789"));
        orders.add(order("ORD-002", "Gadget B", 5, 149.95,
                "Bob Smith", "bob@acme.com", "987-65-4321"));
        orders.add(order("ORD-003", "Service C", 1, 999.00,
                "Carol Davis", "carol@acme.com", "456-78-9012"));
    }

    private Map<String, Object> order(String id, String product, int qty, double total,
                                       String name, String email, String ssn) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", id);
        o.put("product", product);
        o.put("quantity", qty);
        o.put("total", total);
        o.put("status", "CREATED");
        o.put("customerName", name);
        o.put("customerEmail", email);
        o.put("customerSsn", ssn);
        return o;
    }
}