# Non-Intrusive Entitlement Enforcement on OpenAPI-Generated Code

This example demonstrates how to add OAC entitlement enforcement to a service built with
[OpenAPI Generator](https://github.com/OpenAPITools/openapi-generator) **without modifying any generated code**.

## Core Principle

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     NON-INTRUSIVE ENTITLEMENT LAYER                       │
│                                                                          │
│  Contract: order-service-api.yaml                                         │
│  ┌──────────────────────────────────────────────────────────────── ┐    │
│  │ paths:                                                            │    │
│  │   /orders/{orderId}:                                              │    │
│  │     get:                                                          │    │
│  │       operationId: getOrder                                       │    │
│  │       x-oac-entitlement:        ← Vendor extension — zero code    │    │
│  │         action: READ                                              │    │
│  │         resourceType: order                                       │    │
│  │         resourceIdPath: orderId                                   │    │
│  │                                                                   │    │
│  └──────────────────────────────────────────────────────────────── ┘    │
│                                                  │ Parsed at startup     │
│                                                  ▼                      │
│  Runtime: EntitlementRegistry + OacEnforcementInterceptor               │
│  ┌──────────────────────────────────────────────────────────────── ┐    │
│  │  Request → Interceptor.preHandle() → PDP Check → Allow/Deny      │    │
│  │  Response → FieldMaskResponseAdvice → Mask PII → Client          │    │
│  └──────────────────────────────────────────────────────────────── ┘    │
│                                                  │ No generated code    │
│                                                  ▼                      │
│  Controller: OrderControllerImpl (hand-written, implements OrdersApi)    │
│  ┌──────────────────────────────────────────────────────────────── ┐    │
│  │  @RestController                                                 │    │
│  │  class OrderControllerImpl implements OrdersApi {                │    │
│  │      // ZERO OAC annotations. ZERO OAC imports.                 │    │
│  │      // Pure business logic.                                     │    │
│  │  }                                                               │    │
│  └──────────────────────────────────────────────────────────────── ┘    │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

## Directory Structure

```
non-intrusive-entitlement/
├── order-service-api.yaml              ← OpenAPI contract with x-oac-entitlement
├── pom.xml                             ← Maven with openapi-generator plugin
├── README.md                           ← This file
└── src/main/java/com/oac/example/
    ├── NonIntrusiveEntitlementApplication.java
    ├── controller/
    │   └── OrderControllerImpl.java    ← Implements generated OrdersApi (NO OAC code)
    ├── config/
    │   └── WebMvcConfig.java           ← Registers the interceptor
    └── entitlement/
        ├── OacEntitlementConfig.java   ← Data class for entitlement config
        ├── EntitlementRegistry.java    ← Parses YAML x-oac-entitlement at startup
        ├── OacEnforcementInterceptor.java ← HandlerInterceptor (HTTP-level PDP check)
        └── FieldMaskResponseAdvice.java   ← ResponseBodyAdvice (masks PII fields)
```

## How It Works

### Step 1: Define Entitlements in the OpenAPI Contract

Each protected operation has an `x-oac-entitlement` vendor extension:

```yaml
/orders/{orderId}:
  get:
    operationId: getOrder
    x-oac-entitlement:
      action: READ
      resourceType: order
      resourceIdPath: orderId
      enforceFieldMask: true
```

### Step 2: Generate Code

```bash
mvn openapi-generator:generate
```

Generates `OrdersApi.java` and `OrderResponse.java` — never modified.

### Step 3: Implement the Generated Interface

`OrderControllerImpl` implements `OrdersApi` with pure business logic — zero entitlement code.

### Step 4: EntitlementRegistry Parses the YAML

At startup, `EntitlementRegistry` reads the YAML contract and builds a `Map<operationId, OacEntitlementConfig>`.

### Step 5: OacEnforcementInterceptor Enforces

Before every HTTP request:
1. Resolves the `operationId` from path + method
2. Looks up the entitlement config from the registry
3. Extracts `subjectId` from `X-User-Id` header (or `X-Service-Id`)
4. Extracts `resourceId` from the URL path
5. Calls PDP → allows or returns 403

### Step 6: FieldMaskResponseAdvice Masks PII

After every response, `FieldMaskResponseAdvice` intercepts the body and applies field masks to `customerEmail`, `customerSsn`, and `customerPhone`.

## Running the Example

### Prerequisites
- Java 21+
- Docker (for MongoDB)

### 1. Start MongoDB + PDP

```bash
docker run -d -p 27017:27017 --name oac-mongo mongo:7.0
cd services/policy-decision-service
mvn spring-boot:run -Dspring-boot.run.profiles=mongodb
```

### 2. Seed Policies

Configure policies in the PDP's MongoDB that allow/deny `READ` for "order", `APPROVE` for "order", etc.

### 3. Start the Example Service

```bash
cd examples/non-intrusive-entitlement
mvn openapi-generator:generate
mvn spring-boot:run
```

### 4. Test Scenarios

```bash
# Get order (Alice — allowed)
curl -s -H "X-User-Id: alice" http://localhost:8082/orders/ORD-001 | jq
# → 200 OK, customerEmail: "a***@acme.com" (masked by ResponseBodyAdvice)

# Get order (attacker — denied by interceptor)
curl -s -H "X-User-Id: attacker" http://localhost:8082/orders/ORD-001 | jq
# → 403 Forbidden

# List orders (auditor — allowed, all PII masked)
curl -s -H "X-User-Id: auditor" http://localhost:8082/orders | jq
# → 200 OK

# Approve order (bob — depends on PDP policy)
curl -s -X POST -H "X-User-Id: bob" http://localhost:8082/orders/ORD-001/approve | jq
# → 200 OK or 403 Forbidden

# Aggregate (reporting-service workload)
curl -s -H "X-Service-Id: reporting-service" http://localhost:8082/orders/aggregate | jq
# → 200 OK with aggregated data
```

## Key Design Points

| Aspect | Approach |
|---|---|
| **Generated code modified?** | ❌ Never |
| **Annotations on controllers?** | ❌ None — `OrderControllerImpl` has no OAC imports |
| **Controller code changed for entitlements?** | ❌ No — pure business logic |
| **Entitlement metadata source?** | `x-oac-entitlement` vendor extensions in YAML |
| **Runtime PDP integration?** | `HandlerInterceptor.preHandle()` |
| **Field masking mechanism?** | `ResponseBodyAdvice` global advice |
| **Identity extraction?** | HTTP headers (`X-User-Id`, `X-Service-Id`) |
| **Resource ID extraction?** | URL path variable regex from YAML template |