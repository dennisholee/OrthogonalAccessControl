# ADR: Datastore Pattern by Phase for Policy and Relationship Workloads

- Status: Proposed
- Date: 2026-05-11
- Owners: Platform Architecture, Security Architecture
- Related: [PRD.md](../../PRD.md), [ARCHITECTURE.md](../../ARCHITECTURE.md)

## Context

The platform needs low-latency authorization decisions, immutable auditability, and safe evolution from initial rollout to enterprise scale. Current docs require graph-style relationship evaluation and caveat-aware checks, while also emphasizing implementation practicality in early phases.

## Decision Drivers

- Fast Phase 1 delivery for Spring Boot service onboarding
- Deterministic decision behavior with explicit deny precedence
- Causal safety support through consistency token flows
- Scalable relationship traversal for later phases

## Options Considered

1. Start with relational storage for policy and relationship records, with abstraction for later graph-optimized backends.
2. Start directly with graph-native storage for all relationship and policy workloads.
3. Hybrid from day one: relational policy registry plus graph-native relationship store.

## Decision

Adopt option 1 for Phase 1 and Phase 2 entry: relational-first with strict storage abstraction boundaries and migration-ready tuple model. Re-evaluate graph-native migration gate at the end of Phase 2 based on measured LookupResources and traversal SLOs.

## Consequences

### Positive

- Reduces initial platform complexity and accelerates first production capability.
- Preserves compatibility with existing enterprise operational patterns.
- Enables controlled migration once real workload data is available.

### Negative

- May require later migration for deep traversal performance.
- Requires disciplined abstraction to avoid data-model lock-in.

### Risks and Mitigations

- Risk: migration complexity if schema diverges from graph semantics -> Mitigation: keep tuple-compatible schema and migration rehearsal in non-prod.
- Risk: insufficient traversal performance for complex ReBAC paths -> Mitigation: define Phase 2 performance gate and benchmark suite early.

## Follow-up Actions

1. Add tuple-compatible persistence contract in decision service design.
2. Define measurable migration trigger criteria in architecture roadmap.

## Open Questions

- Which exact traversal-depth and LookupResources latency thresholds should trigger graph-native migration?
