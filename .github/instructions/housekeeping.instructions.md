---
applyTo: "**/*"
description: "Use when cleaning up redundant code, tests, and resource files to keep the repository lean and maintainable."
---

# Housekeeping Rules

## Purpose

Remove redundant artifacts safely while preserving required behavior, enforcement gates, and test coverage.

## Redundancy Criteria

Treat an artifact as redundant only when at least one is true:

- It is fully superseded by a canonical implementation.
- It duplicates behavior already covered by another maintained path.
- It is unused dead code or unreachable test/resource content.
- It is a stale migration intermediate no longer referenced.

## Mandatory Safety Checks

Before deletion:

- Confirm no active references remain in source, tests, configs, or docs.
- Confirm no build/test runner relies on the file by convention.
- Confirm enforcement tests and governance checks still apply after cleanup.

After deletion:

- Run full verification (`mvn -q verify` for this repository).
- Ensure architecture, BDD, unit, and integration tests remain green.

## Preferred Canonical Paths In This Repository

- Policy behavior specifications: `services/policy-decision-service/src/test/resources/features`.
- BDD runner and step definitions: `services/policy-decision-service/src/test/java/com/oac/decision/bdd`.
- Feature-driven enforcement guard: `services/policy-decision-service/src/test/java/com/oac/decision/feature/FeatureDrivenPracticeEnforcementTest.java`.

Do not remove canonical paths unless replacing them in the same change with equivalent or stronger coverage and enforcement.

## Deletion Discipline

- Remove the smallest safe set of files.
- Avoid deleting reusable abstractions if they can be narrowed instead.
- Update instructions/enforcement tests in the same PR when canonical paths change.
- Keep repository docs aligned with cleanup decisions.

## Review Checklist

- Redundant artifacts identified with evidence.
- No remaining references to deleted files.
- Build and all test layers pass.
- Instruction and enforcement files remain consistent.
