---
applyTo: "**/*.{md}"
description: "Use when editing requirements, architecture, ADR, or governance documentation to keep structure, wording, and traceability consistent."
---

# Documentation Style Rules

## Source of Truth

- Requirements source: [PRD.md](../../PRD.md)
- Architecture source: [ARCHITECTURE.md](../../ARCHITECTURE.md)
- Agent workflow source: [AGENTS.md](../../AGENTS.md)

When wording conflicts occur, align to these documents and add a concise open question or decision note.

## Structure Rules

- Use a single H1 title per document.
- Keep numbered section hierarchies stable once published.
- Add new sections by appending numbers; do not renumber existing sections unless explicitly requested.
- Prefer short, testable statements over narrative paragraphs.
- Keep bullet lists flat and action-oriented.

## Requirements Writing Rules

- Use RFC-style requirement language:
  - `MUST` for non-negotiable behavior
  - `SHOULD` for strong recommendations
  - `MAY` for optional behavior
- For new functional requirements, use stable IDs in `FR-<number>` format.
- For new non-functional requirements, use stable IDs in `NFR-<number>` format.
- Each requirement statement should be independently testable.
- Avoid vague terms such as `fast`, `secure enough`, or `appropriate` without measurable criteria.

## Terminology Rules

Use canonical terms consistently:

- tenant
- geography
- market
- line of business
- channel
- explicit deny precedence
- caveat-aware evaluation
- consistency token

Avoid deprecated or vendor-specific terminology in generalized docs.

## Cross-Document Sync Rules

When changing [PRD.md](../../PRD.md), verify matching impacts in [ARCHITECTURE.md](../../ARCHITECTURE.md).

When changing [ARCHITECTURE.md](../../ARCHITECTURE.md), verify requirement traceability back to [PRD.md](../../PRD.md).

For architecture-impacting decisions, add or update an ADR entry and reference it.

## Evidence and Traceability Rules

- Link every major behavior change to at least one requirement or architecture section.
- Include brief rationale for policy/governance changes.
- Preserve auditability wording for maker-checker and separation-of-duties controls.
- Keep examples realistic but free of sensitive data.

## Review Checklist

Before finalizing a documentation change, confirm:

1. Terms are canonical and consistent.
2. New statements are testable and measurable.
3. PRD and architecture are synchronized where required.
4. Governance and boundary controls are unchanged or intentionally updated.
5. References resolve to existing repository documents.
