---
applyTo: "**/*.{java,kt,groovy,xml,yaml,yml,json,md}"
description: "Use when creating or updating tests, test plans, and validation criteria for authorization behavior and policy governance."
---

# Test Strategy Rules

## Source of Truth

- Requirements source: [PRD.md](../../PRD.md)
- Architecture source: [ARCHITECTURE.md](../../ARCHITECTURE.md)

If test expectations conflict with requirements or architecture, align with these documents and add a decision note.

## Mandatory Test Coverage Areas

Always include tests for these behaviors when relevant code or contracts are added:

- Decision precedence ordering with explicit deny dominance.
- Boundary enforcement for tenant, geography, market, line of business, and channel dimensions.
- RBAC, PBAC, and Attribute-Assisted ReBAC interaction in a single decision context.
- Consistency-token behavior for read-after-write critical paths.
- Fail-closed and approved fail-open endpoint classifications.
- LookupResources-style filtering guarantees (no over-returned resources).

## Policy Lifecycle and Governance Tests

For policy/admin workflows, include tests for:

- Maker-checker approval requirements before production promotion.
- Separation-of-duties constraints between author, approver, and tenant admin roles.
- Policy lifecycle transitions (draft, validated, approved, staged, active, deprecated, retired).
- Rollback behavior to last known good policy bundle.
- Audit evidence emission for policy changes and decision traces.

## Test Types and Expectations

### E2E Feature Tests (Gate Required)
- **Definition**: HTTP-driven, real dependencies (Testcontainers MongoDB), front-to-back verification.
- **Framework**: Cucumber (BDD) scenarios in `policy-decision.feature`.
- **Coverage**: All mandatory behavior areas (precedence, boundaries, ReBAC, caveats, field access, consistency tokens, admin lifecycle, lookup resources, governance).
- **Execution**: Runs during every `mvn test` via `maven-surefire-plugin` as part of the `**/*Test.java` suite. A failure blocks the build.
- **Evidence**: Each scenario produces screen-capture artifacts in `target/screen-capture/{feature}/{scenario}/` containing:
  - `00-seed-data.json` — MongoDB documents seeded before the test
  - `01-request.json` — HTTP request payload + headers
  - `02-response.json` — HTTP response status + body
  - `03-post-state.json` — MongoDB state after the test
  - `04-verification-log.txt` — assertion results

### Integration Tests (Supplemental)
- **Definition**: Full Spring Boot context, Testcontainers MongoDB, direct bean calls or TestRestTemplate.
- **Framework**: JUnit 5 with `*IT.java` suffix.
- **Execution**: Runs via `maven-failsafe-plugin` during `mvn verify`. Does not block `mvn test`.
- **Coverage**: Deeper isolated flows (ReBAC traversal depth, MongoDB query generation, policy conflict detection).

### Unit Tests (Minimal)
- Architecture rule tests only (e.g., PortsAndAdaptersArchitectureTest).
- No mock-based ControllerTest or ServiceTest classes. All API-layer tests must be Cucumber BDD E2E scenarios.
- Pure logic tests (e.g., precedence ordering, condition evaluation) are acceptable if they cannot be expressed as Cucumber scenarios.

### Contract Tests
- Decision and admin API request/response compatibility, validated against OpenAPI specs.
- Future: generated from `contracts/decision-api.yaml` and `contracts/admin-api.yaml`.

### Resilience Tests
- Timeout, dependency failure, stale-cache, and regional failover scenarios.
- Future: chaos-engineering style, not yet required for Phase 1.

## Test Structure Rules

1. **No `@WebMvcTest` or mock-based controller tests.** All HTTP-level verification must go through Cucumber BDD with real Testcontainers dependencies.
2. **Every new API endpoint requires at least one Cucumber scenario** that:
   - Seeds dependencies via MongoDB
   - Makes an HTTP request
   - Asserts HTTP status + business response
3. **Every new endpoint must include a negative test** (e.g., missing boundary, unauthorized subject, invalid transition).
4. **Screen-capture evidence is mandatory for E2E scenarios.** Run `mvn test -pl :policy-decision-service` and verify `target/screen-capture/` is populated.

## Data and Security Test Rules

- Use synthetic test data only; do not include real PII or sensitive financial values.
- Validate that logs and test outputs avoid sensitive attribute leakage.
- Include negative tests for unauthorized cross-boundary access attempts.

## Quality Gates

Before marking work complete:

- All mandatory behavior tests for affected areas pass.
- No critical regressions in deny/allow boundary controls.
- Test evidence is traceable to requirement and architecture sections.
- New behavior includes at least one negative-path test and one auditability assertion.
