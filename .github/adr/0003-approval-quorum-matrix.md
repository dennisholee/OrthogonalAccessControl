# ADR: Approval Quorum and Seniority Matrix for Policy Promotion

- Status: Proposed
- Date: 2026-05-11
- Owners: Policy Governance Council
- Related: [PRD.md](../../PRD.md), [ARCHITECTURE.md](../../ARCHITECTURE.md)

## Context

Maker-checker and separation-of-duties are required, but approval quorum and seniority requirements for high-risk changes are not yet fixed.

## Decision Drivers

- Strong governance and SoD enforcement
- Operational clarity for release gates
- Reduced chance of unilateral risky changes

## Options Considered

1. Single approver for all policy changes.
2. Risk-tiered quorum with independent approver roles.
3. Fixed two-approver model regardless of risk.

## Decision

Adopt risk-tiered quorum with mandatory independence from author:

- Low impact: 1 approver (domain policy approver)
- Medium impact: 1 approver plus policy owner acknowledgement
- High impact: 2 approvers, including one security governance approver
- Critical impact: 2 approvers, including one senior security approver and one business control owner

The policy author cannot be an approver for the same promotion.

## Consequences

### Positive

- Aligns control strength with potential blast radius.
- Establishes explicit SoD and accountability.
- Supports consistent audit interpretation.

### Negative

- Higher coordination cost for urgent high-risk changes.
- Needs robust role assignment management.

### Risks and Mitigations

- Risk: approval bottlenecks for critical changes -> Mitigation: pre-defined on-call approver roster and escalation path.
- Risk: role ambiguity across domains -> Mitigation: maintain governance role catalog with ownership per domain.

## Follow-up Actions

1. Add approver role validation to promotion workflow contract.
2. Define SLA targets for high and critical approvals.

## Open Questions

- Should critical changes require regional approver representation when multi-region impact exists?
