# ADR: Endpoint Classification Rules for Fail-Open Exceptions

- Status: Proposed
- Date: 2026-05-11
- Owners: Platform Security, Application Security
- Related: [PRD.md](../../PRD.md), [ARCHITECTURE.md](../../ARCHITECTURE.md)

## Context

The architecture permits approved fail-open behavior for selected endpoints, but classification rules are not finalized. Incorrect fail-open classification can create broad unauthorized access risk.

## Decision Drivers

- Preserve default-deny safety posture
- Ensure service continuity for low-risk operations
- Keep classification deterministic and auditable

## Options Considered

1. Disallow fail-open entirely.
2. Allow service teams to decide fail-open ad hoc.
3. Central classification policy with narrow, pre-approved exception types.

## Decision

Adopt central classification with default fail-closed. Fail-open is allowed only for endpoints that are all of:

- Read-only and non-privileged
- Non-regulated and non-sensitive data scope
- No cross-tenant, cross-geography, or cross-line-of-business access path
- Explicitly tagged and approved through governance workflow

All fail-open events must emit high-signal audit and operational alerts.

## Consequences

### Positive

- Maintains secure baseline while allowing controlled resilience.
- Produces clear and reviewable exception boundaries.
- Enables consistent runtime behavior across services.

### Negative

- Adds governance overhead for endpoint onboarding.
- May require endpoint refactoring to meet eligibility criteria.

### Risks and Mitigations

- Risk: misclassification due to incomplete endpoint metadata -> Mitigation: require classification checklist and automated policy linting.
- Risk: silent fail-open usage under dependency outage -> Mitigation: mandatory alerts, dashboards, and periodic certification of fail-open inventory.

## Follow-up Actions

1. Define endpoint metadata schema for classification and evidence.
2. Add periodic attestation review for fail-open endpoint list.

## Open Questions

- Should temporary fail-open approvals have mandatory expiry windows by default?
