# Skill: Policy-Architecture Sync

## Purpose

Keep [PRD.md](../../../PRD.md) and [ARCHITECTURE.md](../../../ARCHITECTURE.md) synchronized whenever requirements or technical design change.

## When To Use

Use this skill when asked to:

- Update requirements and architecture in one change.
- Prevent requirement/design drift.
- Add or revise governance, security, or runtime flow behavior.

## Inputs

- Requested change summary.
- Affected sections in [PRD.md](../../../PRD.md).
- Affected sections in [ARCHITECTURE.md](../../../ARCHITECTURE.md).

## Workflow

1. Map change type:
   - Requirement-only impact
   - Architecture-only impact
   - Cross-document impact
2. Apply updates with mirrored intent:
   - Requirement statements remain implementation-agnostic.
   - Architecture sections provide concrete mechanism and data/control flow.
3. Validate parity:
   - Boundary dimensions appear identically in both docs.
   - Decision semantics and precedence are aligned.
   - Governance lifecycle states and controls match.
4. Add trace note:
   - Add a short section-level note indicating the paired update location in the counterpart doc.
5. Run drift checks:
   - Confirm hooks under [.github/hooks](../../hooks) remain compatible with changed terminology.

## Expected Output

- Updated sections in both documents.
- A short change log entry listing:
  - Requirement section updated
  - Architecture section updated
  - Why both were changed together

## Definition Of Done

- No contradictory statements between PRD and architecture.
- Terminology is consistent and vendor-neutral.
- Reviewer can trace each architecture change to a requirement and vice versa.
