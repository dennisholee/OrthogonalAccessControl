# Skill: Policy Cohesion Review

## Purpose

Evaluate requirements and policy definitions for clarity, cohesion, and contradiction risk before implementation.

## When To Use

Use this skill when asked to:

- Review whether requirements are cohesive and non-conflicting.
- Assess enterprise readiness of policy requirements.
- Validate if policy management controls are complete.

## Required Sources

- [PRD.md](../../../PRD.md)
- [ARCHITECTURE.md](../../../ARCHITECTURE.md)
- Relevant ADRs under [.github/adr](../../adr) when available.

## Review Method

1. Find requirement conflicts:
   - Explicit deny vs allow exceptions.
   - Boundary dimensions present in one doc but absent in another.
   - Consistency-token behavior mismatch.
2. Check testability:
   - Convert each requirement to at least one observable assertion.
   - Identify any vague language (`fast`, `sufficient`, `adequate`) and replace with measurable targets.
3. Check operational completeness:
   - Maker-checker and separation-of-duties coverage.
   - Rollback and audit evidence behavior.
   - Failure-mode behavior and endpoint classification.
4. Check terminology consistency:
   - Canonical terms only.
   - No deprecated/vendor-specific terms in generalized docs.

## Output Format

Return findings first, ordered by severity:

1. Severity (`High`, `Medium`, `Low`)
2. Problem statement
3. Why it matters
4. File evidence (path + line)
5. Recommended fix

Then include:

- Overall verdict: `complete`, `mostly complete`, or `incomplete`
- Top 3 remediation actions

## Completion Criteria

- No unresolved `High` severity cohesion conflicts.
- Ambiguous requirements either clarified or captured as explicit open questions.
- Review output provides actionable fixes, not only commentary.
