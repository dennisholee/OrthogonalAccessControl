# ADR: Simulation Coverage Thresholds by Risk Class

- Status: Proposed
- Date: 2026-05-11
- Owners: Security Governance, Platform Engineering
- Related: [PRD.md](../../PRD.md), [ARCHITECTURE.md](../../ARCHITECTURE.md)

## Context

Release gates require simulation coverage checks before policy promotion, but quantitative thresholds are not finalized. Enterprise governance needs measurable criteria to prevent low-confidence changes from reaching production.

## Decision Drivers

- Predictable policy promotion controls
- Reduced risk of unintended broad access grants
- Auditability of change confidence decisions

## Options Considered

1. No numeric thresholds, rely on manual reviewer judgment.
2. Single global threshold for all policy changes.
3. Risk-tiered thresholds aligned to blast-radius class.

## Decision

Adopt risk-tiered simulation thresholds:

- Low impact: at least 80% scenario coverage
- Medium impact: at least 90% scenario coverage
- High and critical impact: at least 95% scenario coverage plus mandatory targeted negative-path scenarios for boundary violations

## Consequences

### Positive

- Enforces consistent quality gates across teams.
- Aligns verification strictness with change risk.
- Improves audit defensibility of promotion decisions.

### Negative

- Requires stronger simulation tooling and test data management.
- Can slow high-impact change rollout if coverage assets are weak.

### Risks and Mitigations

- Risk: teams game coverage metric without representative scenarios -> Mitigation: require named scenario sets and reviewer sign-off on scenario quality.
- Risk: emergency fixes blocked by thresholds -> Mitigation: allow break-glass override with post-change attestation window.

## Follow-up Actions

1. Define simulation scenario taxonomy for tenant, geography, market, line of business, and channel boundaries.
2. Add gate outputs to immutable audit evidence.

## Open Questions

- Should specific policy families (for example global denies) require 100% defined scenario packs?
