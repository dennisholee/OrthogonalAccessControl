---
applyTo: "**/*.{yaml,yml,json,java,md}"
description: "Use when creating or updating authorization decision/admin API contracts, schemas, and related request/response models."
---

# API Contract Rules

## Source of Truth

- Requirements source: [PRD.md](../../PRD.md)
- Architecture source: [ARCHITECTURE.md](../../ARCHITECTURE.md)

When requirements conflict, align with these documents and add a decision note instead of guessing.

## Required Contract Characteristics

- Keep contracts versioned and backward-compatible by default.
- Include a stable `decisionCode` and structured `errors` shape for all non-success outcomes.
- Support idempotency semantics for mutation-style administration operations.
- For critical consistency paths, support `consistencyToken` request handling.
- Keep security-sensitive fields out of logs and responses unless explicitly required.

## Decision API Expectations

For `CheckPermission` and `LookupResources` related contracts:

- Model explicit subject, resource, action, tenant, and organization scope fields.
- Include runtime context needed for boundary enforcement (market, line of business, channel-related context).
- Return matched policy references and explainability evidence references where applicable.
- Preserve deterministic decision semantics (`allow`, `deny`, optional conditional or obligation outputs as defined by current docs).

## Admin API Expectations

For policy and relationship administration contracts:

- Represent policy lifecycle states and promotion workflow transitions.
- Support approval workflow metadata and audit traceability fields.
- Include pagination and filtering for query/list endpoints.
- Define retryability guidance in error responses.

## Change Discipline

- Do not introduce breaking field renames/removals without an explicit compatibility plan.
- If adding new required fields, include a migration strategy for existing clients.
- Keep contract examples realistic and aligned with enterprise boundary controls.
