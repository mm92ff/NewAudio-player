# CLAUDE.md

## Read order

Before doing repository work, read these files in this order:

1. `CLAUDE.md` (this file)
2. `SOUL.md`
3. `MEMORY.md`

When available, also load:
- `governance/profiles/full/SOUL.md` for fuller governance context
- `governance/profiles/full/MEMORY.md` for fuller memory context
- `docs_context/profiles/full/core.md` for output and language guidance
- The language-specific context file matching the active profile's `code_language`:
  - `docs_context/profiles/full/language_python.md` for Python projects
  - `docs_context/profiles/full/language_kotlin.md` for Kotlin/Android projects

## Active profile

The active task profile is determined at runtime. Before any iteration work:

1. Read `workflow/runtime/app_state.json`
2. Extract the `profile_path` field — this is the path to the active task profile
3. Load that profile and read its `code_language` and `iterations` fields
4. Use `code_language` to select the matching language context file above

Do not assume a fixed profile path. The user may switch profiles via `select_profile.ps1`.

## Workflow contract

This repository uses two complementary workflow tracks with native Claude Code subagents:

### Code / repository workflow
1. **Evaluation** (`@agent-evaluator`) — analyze one active area, gather evidence, define a scoped plan, and define acceptance checks.
2. **Implementation** (`@agent-implementer`) — apply the smallest defensible change for the active area, respecting the iteration's `implementation_mode`.
3. **Review** (`@agent-reviewer`) — review the actual diff, checks, and scope discipline. Return strict JSON only.

### Research / information workflow
1. **Search** (`@agent-searcher`) — gather evidence, update the evidence pack, and add source-linked claims to the ledger.
2. **Verification** (`@agent-verifier`) — assess source quality, set claim statuses, and return strict JSON only.
3. **Finalization** (`@agent-finalizer`) — synthesize only verified claims into a final output.

If review is denied:
- implementation must address the review findings,
- review must re-run,
- continue until accepted or the run is stopped.

## Scope boundary

- Work only inside the selected target repository.
- Keep scope limited to the current active iteration.
- If adjacent issues are discovered, record them as dependencies or follow-up items; do not widen scope.
- Prefer small, reversible changes over broad refactors.

## One-shot execution policy

This bundle is designed for a single start prompt that runs the full loop automatically.

Default execution order:
1. read `app_state.json` and load the active task profile,
2. detect enabled iterations,
3. ensure local git workflow readiness,
4. for each active iteration, explicitly invoke `@agent-evaluator`, then `@agent-implementer`, then `@agent-reviewer`,
5. if review returns `DENIED`, feed `required_fixes` back to `@agent-implementer` and re-run `@agent-reviewer`,
6. after acceptance, create a local commit for the accepted work,
7. continue to the next active iteration.

Do not pause for intermediate user approval unless the user explicitly asked for manual checkpoints.

## Git bootstrap and commit policy

Treat a local git workflow as part of the default setup for this bundle.

If `.git/` is missing:
- run `git init`,
- create a suitable `.gitignore` if missing,
- inspect `git status`,
- create a local baseline commit for the current project state before iteration-specific implementation when feasible.

If `.git/` exists but `.gitignore` is missing:
- create a suitable `.gitignore`,
- include it in the next local commit.

During the run:
- use local commits only,
- never push,
- never add or change remotes,
- never perform destructive git operations such as `reset --hard`, `clean -fd`, force pushes, or branch deletion unless the user explicitly requests them.

Commit policy:
- prefer one local commit per accepted iteration,
- if bootstrap work happened first, create a baseline/bootstrap commit before iteration commits when practical,
- use clear commit messages that mention the active iteration title,
- do not wait for extra approval for local commits.

## Output contract

Evaluation output must be markdown with these sections:

## Summary
## Plan
## Acceptance Checklist
## Risks

Implementation output must be markdown with these sections:

# Implementation
## Changed Files
## What Was Fixed
## Checks Run
## Open Risks
## Git Actions

Review output must be **strict JSON only** with fields:

- `verdict`: `ACCEPTED` or `DENIED`
- `reason`: short, concrete reason
- `required_fixes`: array of actionable fixes; empty when accepted

## Validation rules

- Run the lightest credible checks first.
- Required and optional checks from the active iteration prompt take precedence.
- If a listed tool is missing, report it as `tool missing: <n>` and continue.
- Do not install tools or dependencies unless the user explicitly requests that.

## Skill location

The orchestration skill is at:
`.claude/skills/iterative-autocoder-style-workflow/`

Helper scripts:
- `.claude/skills/iterative-autocoder-style-workflow/scripts/render_prompt.py`
- `.claude/skills/iterative-autocoder-style-workflow/scripts/workflow_reporter.py`

## Available profiles

Profiles are stored under `.claude/profiles/`. Use `select_profile.ps1` to change the active profile.
Use `IterationManager.ps1` to enable or disable individual iterations within a profile.

Current profiles in this bundle:
- `task_profile_python_best_practices_iterative.json` — Python, 23 areas, full best-practices suite
- `task_profile_python_desktop_tool.json` — Python, desktop tool variant
- `task_profile_kotlin_android_studio_iterative.json` — Kotlin/Android, 21 areas, enterprise-grade
- `task_profile_kotlin_android_studio_simple_app.json` — Kotlin/Android, 12 areas, small-app scoped
- `task_profile_research_evidence_iterative.json` — research workflow with evidence pack, claim ledger, verification, and final synthesis


## Research workflow location

The research orchestration skill is at:
`.claude/skills/research-evidence-workflow/`

Runtime and evidence artifacts:
- `workflow/runtime/research_state.json`
- `workflow/research/evidence_pack.md`
- `workflow/research/claim_ledger.json`
- `workflow/reports/research_verification_report.json`
- `workflow/reports/research_final_output.md`
