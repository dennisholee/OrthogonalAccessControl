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

- Unit tests: precedence logic, condition evaluation, and edge cases.
- Integration tests: enforcement library to decision service to graph/attribute dependencies.
- Contract tests: decision and admin API request/response compatibility.
- Resilience tests: timeout, dependency failure, stale-cache, and regional failover scenarios.

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
