# Pull Request Checklist

## Summary

- Describe what changed and why.
- Reference related requirement, architecture, or ADR updates when applicable.

## Validation

- [ ] Local build succeeds.
- [ ] Full verification completed with `mvn -q verify`.
- [ ] Added or updated tests for behavior changes.

## Housekeeping And Redundancy Controls

- [ ] Reviewed against [.github/instructions/housekeeping.instructions.md](.github/instructions/housekeeping.instructions.md).
- [ ] Removed only the minimum safe set of redundant code/resource files.
- [ ] Confirmed no remaining references to deleted or consolidated artifacts.
- [ ] Confirmed no enforcement drift in architecture, BDD, and governance checks.

## Architecture And Governance

- [ ] Preserved ports-and-adapters boundaries.
- [ ] Preserved explicit deny precedence and boundary controls.
- [ ] Kept policy governance controls (maker-checker, separation of duties, auditability) intact.

## Documentation Sync

- [ ] Updated [PRD.md](PRD.md) when requirement-level behavior changed.
- [ ] Updated [ARCHITECTURE.md](ARCHITECTURE.md) when design-level behavior changed.
- [ ] Updated relevant instruction files when conventions changed.
