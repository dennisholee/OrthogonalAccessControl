# Agent Instructions

## Repository Purpose

This repository currently contains product and architecture design artifacts for an orthogonal access control platform for Spring Boot microservices. The source of truth is documentation-first.

## Canonical References

- Product requirements: [PRD.md](PRD.md)
- Target architecture: [ARCHITECTURE.md](ARCHITECTURE.md)

## Repository Skills

- Skill index: [.github/skills/README.md](.github/skills/README.md)
- Policy definition: [.github/skills/policy-definition/SKILL.md](.github/skills/policy-definition/SKILL.md)
- Policy cohesion review: [.github/skills/policy-cohesion-review/SKILL.md](.github/skills/policy-cohesion-review/SKILL.md)
- Policy-architecture sync: [.github/skills/policy-architecture-sync/SKILL.md](.github/skills/policy-architecture-sync/SKILL.md)

## Repository Instructions

- API contracts: [.github/instructions/api-contracts.instructions.md](.github/instructions/api-contracts.instructions.md)
- Test strategy: [.github/instructions/test-strategy.instructions.md](.github/instructions/test-strategy.instructions.md)
- Documentation style: [.github/instructions/doc-style.instructions.md](.github/instructions/doc-style.instructions.md)
- Housekeeping: [.github/instructions/housekeeping.instructions.md](.github/instructions/housekeeping.instructions.md)

Always read these instruction documents before proposing implementation details.

## Current Repository State

- Initial Spring Boot implementation scaffolding is present.
- Maven multi-module build and tests are available via `mvn test`.
- Work now spans architecture, governance, and phased implementation delivery.

## Working Rules For Coding Agents

- Treat [PRD.md](PRD.md) as the requirements contract and [ARCHITECTURE.md](ARCHITECTURE.md) as the technical blueprint.
- Do not invent conflicting requirements. If a requirement is unclear, add a concise decision note or open question instead of guessing.
- Preserve strict policy boundaries: tenant, geography, market, line of business, and channel.
- Preserve explicit deny precedence and caveat-aware evaluation behavior described in the docs.
- Keep policy governance controls intact: maker-checker, separation of duties, approval gates, and auditability.

## If Asked To Start Implementation

Use this order of work:

1. Confirm or create Architecture Decision Records for unresolved items in [ARCHITECTURE.md](ARCHITECTURE.md).
2. Define decision and admin API contracts from [PRD.md](PRD.md) and [ARCHITECTURE.md](ARCHITECTURE.md).
3. Scaffold the Spring Boot policy enforcement and decision service skeletons.
4. Add tests for decision precedence, boundary controls, and consistency-token behavior.
5. Add observability and audit event structures before feature expansion.

## Documentation Update Convention

When changing architecture or requirements:

- Update [PRD.md](PRD.md) for requirement-level changes.
- Update [ARCHITECTURE.md](ARCHITECTURE.md) for design-level changes.
- Keep terminology consistent across both files.
