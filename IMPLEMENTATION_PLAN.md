# Implementation Plan

## Status

Planning and implementation phase started on 2026-05-11.
Phase 1 foundation completion recorded on 2026-05-12.
Phase 3 governance and observability completion recorded on 2026-05-12.
Phase 4 multi-region and reliability workstream started on 2026-05-12.
Phase 4 multi-region and reliability completion recorded on 2026-05-12.

## Scope

This plan executes the order defined in [AGENTS.md](AGENTS.md):

1. Confirm architecture decisions through ADRs.
2. Define decision and admin API contracts.
3. Scaffold Spring Boot enforcement and decision service skeletons.
4. Add tests for precedence, boundaries, and consistency token behavior.
5. Add observability and audit event structures.

## Completed Bootstrap (Phase Start)

- ADR set created for unresolved architecture decisions:
  - [.github/adr/0001-datastore-pattern.md](.github/adr/0001-datastore-pattern.md)
  - [.github/adr/0002-simulation-thresholds.md](.github/adr/0002-simulation-thresholds.md)
  - [.github/adr/0003-approval-quorum-matrix.md](.github/adr/0003-approval-quorum-matrix.md)
  - [.github/adr/0004-fail-open-classification.md](.github/adr/0004-fail-open-classification.md)
- API contract skeletons started:
  - [contracts/decision-api.yaml](contracts/decision-api.yaml)
  - [contracts/admin-api.yaml](contracts/admin-api.yaml)

## Execution Backlog

## Sprint 0: Contract-First Foundation

- Finalize ADR approvals and status updates.
- Finalize OpenAPI contracts for Decision API and Admin API.
- Define error taxonomy and decisionCode catalog.
- Define consistency token propagation contract for critical flows.

Exit criteria:

- ADR statuses moved to Accepted or explicitly deferred.
- API contracts reviewed by platform and security owners.

## Sprint 1: Service Skeletons

- Create `services/policy-decision-service` Spring Boot module.
- Create `libraries/spring-policy-enforcement-starter` module.
- Implement contract stubs for CheckPermission and LookupResources.
- Implement baseline policy registry integration interface.

Exit criteria:

- Services start and expose health endpoints.
- API stubs return structured responses with error envelope.

Completion status:

- Completed on 2026-05-12.
- Decision service and starter modules are scaffolded and verified.
- Baseline policy registry integration interface is in place.
- CheckPermission now enforces baseline policy outcomes with explicit deny precedence.

## Sprint 2: Authorization Core

- Implement explicit deny precedence engine.
- Implement tenant, geography, market, line-of-business, and channel boundary checks.
- Integrate attribute resolver abstraction.
- Add consistency token read-after-write handling path.

Exit criteria:

- Core decision tests pass for precedence and boundaries.
- Negative cross-boundary tests pass.

Completion status:

- Completed on 2026-05-12.
- Boundary-aware deny path is enforced with deterministic precedence.
- Attribute resolver abstraction is integrated through application output ports.
- Consistency-token mismatch path is implemented for critical checks.
- LookupResources now applies boundary-constrained filtering with paging.

## Sprint 3: Governance and Observability

- Implement maker-checker workflow endpoints and state transitions.
- Emit audit evidence for policy lifecycle and decisions.
- Add metrics, tracing hooks, and high-signal security alerts.

Exit criteria:

- Governance workflow tests pass.
- Audit payload includes policy version and decision evidence references.

Completion status:

- Completed on 2026-05-12.
- Maker-checker workflow endpoints and state transitions are implemented under `/v1/admin/policies`.
- Policy lifecycle and decision-trace audit evidence is emitted and queryable via `/v1/admin/audit-events`.
- Observability counters and high-signal security alert hooks are active for decisions and lifecycle transitions.

## Risks and Dependencies

- Identity claim normalization for market and line of business context.
- Data source authority for organizational hierarchy and channel signals.
- Throughput and latency validation under realistic relationship depth.

## Housekeeping Standard

- Apply repository cleanup rules from [.github/instructions/housekeeping.instructions.md](.github/instructions/housekeeping.instructions.md) whenever deleting or consolidating code, tests, or resources.
- For every cleanup change, confirm there are no remaining references and no enforcement drift.
- Run full post-cleanup verification with `mvn -q verify` before closing the task.

## Immediate Next Actions

1. Prepare post-Phase-4 production hardening backlog with sustained-load tuning and capacity envelopes.
2. Advance ADR status transitions from Proposed to Accepted or Deferred with owner sign-off.
3. Add infrastructure-backed adapters for policy and audit persistence to complement in-memory baseline.

## Sprint 4 Completion Status

- Strict consistency-path checks are implemented for `CheckPermission` requests with explicit deny outcomes when tokens are missing or unverifiable.
- Strict consistency-token controls now also apply to `LookupResources` requests with deterministic empty-result behavior when strict token validation fails.
- Endpoint fail-open and fail-closed outage classification controls are implemented with resilience-focused decision tests.
- Fail-open controls now enforce a cataloged endpoint eligibility registry via `endpointKey` validation during dependency outages.
- Admin DR continuity verification endpoint is implemented at `/v1/admin/recovery/continuity` with coverage for policy-audit continuity assertions.
- DR continuity tests now validate active-policy audit coverage after full promotion to `ACTIVE` state.
- Fail-open eligibility is now externalized through a classpath-backed registry and enforced via `endpointKey` approval checks.
- Regional lag and replica-version simulation controls are now enforced on strict consistency paths for both `CheckPermission` and `LookupResources`.
- DR continuity endpoint now supports failover rehearsal mode (`rehearsal=true`) with explicit rehearsal execution and pass/fail reporting.
- Observability now records regional lag distributions, replica-version gaps, and failover rehearsal outcomes for Phase 4 reliability analysis.
- Externalized fail-open endpoint registry behavior is validated with focused adapter tests.
- Service-level negative-path rehearsal failure validation is in place to ensure continuity gaps are surfaced deterministically.
- Sprint 4 implementation scope is complete for repository-level runtime, contracts, and verification gates.
