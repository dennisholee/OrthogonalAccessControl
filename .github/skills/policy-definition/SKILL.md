# Skill: Policy Definition

## Purpose

Define enterprise-grade access policies for the orthogonal access control model described in [PRD.md](../../../PRD.md) and [ARCHITECTURE.md](../../../ARCHITECTURE.md).

## When To Use

Use this skill when asked to:

- Define new policies for a domain/service.
- Convert business controls into enforceable authorization rules.
- Add caveat/context constraints (tenant, geography, market, line of business, channel).
- Prepare policy proposals for maker-checker approval.

## Inputs Required

- Business objective and protected resource scope.
- Subject types (human, service account, application).
- Action set (`read`, `write`, `approve`, etc.).
- Boundary dimensions and allowed values.
- Risk classification and default fail behavior.

## Workflow

1. Confirm source of truth from [PRD.md](../../../PRD.md) and [ARCHITECTURE.md](../../../ARCHITECTURE.md).
2. Model the access tuple: subject, resource, action, context.
3. Apply orthogonal boundaries:
   - Tenant
   - Geography
   - Market
   - Line of business
   - Channel
4. Add deny-first constraints for prohibited combinations.
5. Add caveat clauses only when deterministic context signals are available.
6. Add explainability metadata (`decisionCode`, rationale text, policy reference).
7. Prepare governance metadata (author, reviewer role, change rationale, expiry/review date).

## Output Template

Use this concise structure for policy proposals:

```markdown
### Policy: <name>
- Intent: <business control>
- Scope: <resource types>
- Subjects: <roles/attributes/relationships>
- Actions: <allowed actions>
- Boundary Constraints: <tenant/geography/market/lob/channel>
- Explicit Deny Rules: <deny conditions>
- Caveats: <runtime conditions>
- Explainability: <decisionCode + evidence references>
- Governance: <maker, checker, effective window, review cadence>
```

## Quality Checks

- No policy bypasses boundary dimensions.
- Explicit deny takes precedence over any allow path.
- Policy language is vendor-neutral.
- Negative test scenarios are listed for cross-boundary access attempts.
- Required audit fields are included.
