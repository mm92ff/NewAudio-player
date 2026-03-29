# MEMORY.md

> Living document. Update this file when durable assumptions about the target project change.

## What this bundle expects

- A repository root containing `CLAUDE.md`, `.claude/agents/`, and `.claude/skills/`.
- An iterative task profile under `.claude/profiles/`.
- A `workflow/runtime/app_state.json` file with a valid `profile_path` pointing to the active profile.
- A user who may choose to work with or without git initialization.

## Stable workflow assumptions

- Phase order is fixed: Evaluation → Implementation → Review.
- Evaluation and Review are read-only.
- Review must return strict JSON only.
- Implementation must respect each iteration's `implementation_mode`.
- Missing tools are reported, not treated as automatic failure.
- The active profile and its `code_language` are resolved from `app_state.json` at the start of every run.

## Repository bootstrap assumptions

- If `.git/` is missing, the workflow still works because `CLAUDE.md` defines the project root.
- Git initialization, baseline commits, and `.gitignore` creation are part of the default automatic flow.
- Progress notes can be written under `workflow/reports/` without requiring git.

## Subagent locations

Claude Code subagents are defined as `.md` files in `.claude/agents/`:
- `.claude/agents/evaluator.md`
- `.claude/agents/implementer.md`
- `.claude/agents/reviewer.md`

Invoke them with: `@agent-evaluator`, `@agent-implementer`, `@agent-reviewer`

## Language context files

Language-specific guidance lives in `docs_context/profiles/full/`:
- `language_python.md` — Python projects
- `language_kotlin.md` — Kotlin/Android projects

The active profile's `code_language` field determines which file to load.

## Things to update in a real project

Add or revise:
- primary test commands,
- lint/type-check commands,
- protected directories,
- generated file rules,
- architecture boundaries,
- framework-specific hazards,
- release or deployment expectations.

## Execution memory

Assume local git bootstrap, `.gitignore`, baseline commit, and per-iteration local commits are normal parts of the workflow unless the user overrides that behavior.
