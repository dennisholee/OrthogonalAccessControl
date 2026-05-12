---
mode: ask
description: "Create an ADR for an unresolved architecture decision (datastore pattern, simulation thresholds, approval quorum, fail-open classification, etc.)."
---

# Create ADR Entry

Create or update an Architecture Decision Record (ADR) for this repository.

## Inputs

- Decision topic: ${input:decision_topic}
- Status: ${input:status}
- Drivers: ${input:drivers}
- Options considered: ${input:options}
- Chosen option: ${input:chosen_option}
- Consequences: ${input:consequences}
- Related PRD/Architecture references: ${input:references}

## Required Behavior

1. Read [PRD.md](../../PRD.md) and [ARCHITECTURE.md](../../ARCHITECTURE.md) first.
2. Use concise, evidence-based language.
3. Do not restate large chunks of source docs; link to them.
4. If unresolved details remain, add explicit open questions.
5. Keep terminology consistent with repository standards.

## ADR Template

Use this exact structure:

```markdown
# ADR: <Decision Title>

- Status: <Proposed|Accepted|Deprecated|Superseded>
- Date: <YYYY-MM-DD>
- Owners: <names or roles>
- Related: [PRD.md](PRD.md), [ARCHITECTURE.md](ARCHITECTURE.md)

## Context

<Problem statement and constraints>

## Decision Drivers

- <driver 1>
- <driver 2>

## Options Considered

1. <option A>
2. <option B>
3. <option C>

## Decision

<Chosen option and rationale>

## Consequences

### Positive

- <benefit>

### Negative

- <trade-off>

### Risks and Mitigations

- <risk> -> <mitigation>

## Follow-up Actions

1. <action>
2. <action>

## Open Questions

- <question if any>
```

## Output Expectations

- If ADRs directory does not exist, propose `.github/adr/` as default location.
- Suggest an ADR filename in `NNNN-short-title.md` style.
- Include a short summary of why this decision should be tracked now.
