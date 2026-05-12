---
applyTo: "services/policy-decision-service/src/test/**/*.{java,feature,properties}"
description: "Use when adding or modifying policy decision tests to enforce feature-driven development practices with Cucumber BDD."
---

# Feature-Driven Testing Rules

## Purpose

Ensure policy behavior is specified and validated as executable features before implementation details drift.

## Required Test Layers

When changing authorization decision behavior, maintain these layers:

- Feature specifications in Gherkin under `src/test/resources/features`.
- Cucumber step definitions under `src/test/java/com/oac/decision/bdd`.
- Unit/feature assertions for deterministic decision semantics.

## Naming and Placement

- Feature files must end with `.feature` and live in `src/test/resources/features`.
- BDD suite runner class must remain `PolicyDecisionFeatureTest` unless explicitly migrated.

## Mandatory Scenario Coverage

For policy decision behavior changes, include or update scenarios for:

- Default deny when no policy matches.
- Allow when ALLOW policy matches.
- Explicit DENY precedence over ALLOW.
- At least one PBAC context-driven allow or deny scenario.

## Tagging and Conventions

- Policy decision feature scenarios must include `@feature-driven` tag.
- Keep scenario titles business-readable and behavior-focused.
- Avoid implementation details in scenario steps.

## Step Definition Rules

- Keep steps deterministic and side-effect free.
- Prefer composition through existing ports/services over direct controller invocation.
- Use fixture-backed matching for deterministic precedence tests.

## Enforcement Expectations

The build enforces feature-driven practice via tests. Do not bypass:

- `FeatureDrivenPracticeEnforcementTest`
- `PolicyDecisionFeatureTest`

If a legitimate change requires updated conventions, update this instruction and related enforcement tests in the same pull request.
