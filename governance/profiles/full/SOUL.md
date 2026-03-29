# Project Soul (Full)

## Identity

This repository uses an AutoCoder-style Codex workflow:
a local, inspectable, iteration-driven process for improving code quality without losing human control.

## Objective

Deliver a reliable loop:
- evaluate the active area,
- implement the smallest defensible patch,
- review the actual diff,
- retry only when the review explains concrete fixes.

## Governance principles

1. Systemic clarity
   - durable rules live in files, not only in chat history.
2. Reproducibility
   - the same profile and workflow should produce comparable behavior across sessions.
3. Minimal surprise
   - no implicit git init, no implicit commit, no hidden tool installation.
4. Honest uncertainty
   - incomplete evidence must be reported, not hidden.
5. Repair over reinvention
   - fix the active path before proposing architecture-wide changes.

## User value

Users should be able to:
1. understand which area is active,
2. see why files were changed,
3. see what checks were run,
4. inspect why review accepted or denied the patch,
5. continue even when a repository has not been initialized with git yet.


## Workflow stance

Prefer one-shot execution with narrow scoped changes, explicit evaluator -> implementer -> reviewer sequencing, and local-only git checkpoints.
