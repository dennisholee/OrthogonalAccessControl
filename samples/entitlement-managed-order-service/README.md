# Entitlement-Managed Order Service — Sample Project

This sample demonstrates how to use the Orthogonal Access Control (OAC) platform to manage entitlements within a REST API service that accesses MongoDB collection data.

## Architecture

```
Client ──→ OrderService (port 8081) ──→ PDP (port 8080) ──→ MongoDB
                                            │
                                            └── policies collection
                                            └── relationships collection
```

The Order Service calls the Policy Decision Service (PDP) before every data access. The PDP evaluates the request against configured policies and returns ALLOW/DENY with optional field-level masks.

## Quick Start

### Prerequisites
- Java 21+
- Docker (for MongoDB)
- Maven

### Step 1: Start MongoDB

```bash
docker run -d -p 27017:27017 --name oac-mongo mongo:7.0
```

### Step 2: Start the PDP

```bash
cd services/policy-decision-service
mvn spring-boot:run -Dspring-boot.run.profiles=mongodb
```

### Step 3: Start the Sample Service (with auto-seed)

```bash
cd samples/entitlement-managed-order-service
mvn spring-boot:run -Dspring-boot.run.profiles=seed
```

The `seed` profile activates a `DemoDataSeeder` component that automatically:
- Drops and seeds 6 policies into the PDP's `oac_authorization.policies` collection
- Drops and seeds 2 ReBAC relationships into `oac_authorization.relationships`
- Drops and seeds 4 sample orders into `oac_sample.orders`

If you prefer to seed data manually without the auto-seed profile:

```bash
# Start without seeding
cd samples/entitlement-managed-order-service
mvn spring-boot:run

# Then seed only orders (via the API)
curl -X POST http://localhost:8081/api/orders/seed
```

### Step 4: Test Demo Scenarios

```bash
# 1. CSR reads an order — PII is masked
curl -s -H "X-User-Id: alice" -H "X-User-Role: csr" \
  http://localhost:8081/api/orders/ORD-001 | jq
# → shows customerEmail: "a***@acme.com", customerSsn: null

# 2. Attacker is blocked (explicit deny)
curl -s -H "X-User-Id: attacker" \
  http://localhost:8081/api/orders/ORD-001 | jq
# → 403 Forbidden

# 3. Auditor sees orders with no PII
curl -s -H "X-User-Id: auditor" -H "X-User-Role: auditor" \
  http://localhost:8081/api/orders | jq
# → all 4 orders returned, customer fields are null

# 4. Reporting service reads aggregate (no human auth needed)
curl -s -H "X-Service-Id: reporting-service" -H "X-Service-Type: workload" \
  http://localhost:8081/api/orders/aggregate | jq
# → totalRevenue, order list without PII

# 5. Manager approves an order (ReBAC)
curl -s -X POST -H "X-User-Id: bob" \
  http://localhost:8081/api/orders/ORD-001/approve | jq
# → 403 Forbidden (no manages relationship to alice)
```

## Demo Scenarios

| # | User | Role | Endpoint | Expected Result |
|---|---|---|---|---|
| 1 | alice | csr | GET /api/orders/ORD-001 | 200 — customerEmail masked |
| 2 | attacker | — | GET /api/orders/ORD-001 | 403 — explicit deny |
| 3 | auditor | auditor | GET /api/orders | 200 — all orders, no PII |
| 4 | reporting-service | workload | GET /api/orders/aggregate | 200 — aggregated data |
| 5 | bob | — | POST /api/orders/ORD-001/approve | 403 — no ReBAC edge |
| 6 | admin | admin | GET /api/orders/ORD-001 | 200 — full visibility |

## How Entitlement Enforcement Works

1. **`@RequireAccess`** annotation on controller methods builds the authorization context
2. **`DecisionClient.checkPermission()`** sends a synchronous REST call to the PDP
3. **PDP's 8-rule chain** evaluates the request against stored policies:
   - Rule 1: ExplicitDenyRule — blocks compromised users immediately
   - Rule 4-5: BoundaryViolation/MissingBoundaryContext — enforces tenant/geo/market/LOB/channel
   - Rule 6: ReBacRelationshipRule — checks relationship edges
   - Rule 7: AllowRule + Caveats — allows with field-mask enrichment
   - Rule 8: DefaultDenyRule — denies if no policy matches
4. **`FieldMaskEnforcer`** applies the returned `AttributeAccessMap` to the response data
5. **`MaskedOrder`** wrapper applies MASK (partial redact), NONE (full redact), or READ (passthrough)

## Files

| File | Description |
|---|---|
| `SampleOrderServiceApplication.java` | Spring Boot main class |
| `controller/OrderController.java` | REST API with 5 endpoints |
| `service/OrderService.java` | Business logic with entitlement enforcement |
| `service/MaskedOrder.java` | Field-level masking wrapper |
| `model/Order.java` | MongoDB document with PII fields |
| `repository/OrderRepository.java` | Spring Data MongoDB repository |
| `config/DecisionClientConfig.java` | REST DecisionClient configuration |
| `config/RestDecisionClient.java` | HTTP client for PDP communication |
| `application.yml` | Service configuration |
| `sample-policies.json` | 6 sample policies for seeding |