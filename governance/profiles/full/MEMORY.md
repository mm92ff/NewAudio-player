# Project Memory (Full)

> Living document. Update when workflow expectations or project constraints change.

## Current state

- Three project-scoped Codex subagents are defined:
  - evaluator
  - implementer
  - reviewer
- A repository skill named `iterative-autocoder-style-workflow` provides the orchestration pattern.
- The task profile source of truth is `.codex/profiles/task_profile_python_best_practices_iterative.json`.
- Current enabled bundled areas:
- Python Best Practices: Validation and Business Logic

## Bootstrap behavior

- `.codex/config.toml` includes `project_root_markers` so Codex can detect the repository root even when `.git/` does not exist.
- Git initialization is intentionally not automatic.
- Workflow reports can be written to `workflow/reports/` without git.

## Review contract

- Review is read-only.
- Review returns strict JSON only.
- If review denies the patch, implementation should address `required_fixes` and retry.

## Recommended future project-specific additions

Add real values for:
- build/test commands,
- forbidden directories,
- generated file policies,
- database migration rules,
- deployment constraints,
- supported Python versions,
- dependency installation policy.


## Execution memory

Assume local git bootstrap, `.gitignore`, baseline commit, and per-iteration local commits are normal parts of the workflow unless the user overrides that behavior.
