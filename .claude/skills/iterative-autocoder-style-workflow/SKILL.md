---
name: iterative-autocoder-style-workflow
description: Use this skill when you want an AutoCoder-style Claude Code workflow with one evaluator, one implementer, and one reviewer per active iteration from a JSON task profile, including automatic local git bootstrap and local commits.
---

Use this skill for repository work that should follow a strict loop:

1. read `workflow/runtime/app_state.json` to resolve the active `profile_path`,
2. load the task profile from that path,
3. determine enabled iterations and the `code_language` of the profile,
4. ensure local git readiness,
5. if the project is not a git repo, run `git init`, create a suitable `.gitignore` if missing, and create a local baseline/bootstrap commit when feasible,
6. work on one active iteration at a time,
7. explicitly invoke `@agent-evaluator`,
8. explicitly invoke `@agent-implementer`,
9. explicitly invoke `@agent-reviewer`,
10. if review returns `DENIED`, run `@agent-implementer` again with the required fixes, then re-run `@agent-reviewer`,
11. after review returns `ACCEPTED`, create a local commit for that accepted iteration,
12. continue with the next enabled iteration.

## Inputs expected by this skill

- `workflow/runtime/app_state.json` with a valid `profile_path` pointing to a task profile under `.claude/profiles/`
- Optional instruction to limit execution to one specific area
- Optional instruction to write progress reports under `workflow/reports/`
- Optional instruction to skip bootstrap if the repository is already correctly initialized

## Required behavior

- Resolve the active profile from `app_state.json` at the start of every run.
- Respect `enabled` in the task profile.
- Respect each iteration's `implementation_mode`.
- Apply `code_language` from the profile to drive language-appropriate tooling, idioms, and checks in all three agents.
- Keep scope limited to the active iteration.
- Treat Evaluation and Review as read-only phases.
- Do not stop to ask for approval between evaluator, implementer, reviewer, bootstrap, or local commits.
- If `.git/` is missing, initialize git locally.
- If `.gitignore` is missing, create a project-appropriate version locally.
- Inspect `git status` before and after iteration work when useful.
- Create local commits only; do not push and do not alter remotes.
- Prefer one local commit per accepted iteration, plus a separate bootstrap/baseline commit when helpful.
- If review denies, keep the fix loop narrow and retry until accepted or clearly blocked by an external constraint.
- Use the `workflow-reporter` helper script to summarize enabled areas and git status when useful.

## Helper scripts

Run from your project root:

```bash
# Show enabled iterations and git status
python .claude/skills/iterative-autocoder-style-workflow/scripts/workflow_reporter.py --write-report

# Generate a ready-to-paste orchestration prompt
python .claude/skills/iterative-autocoder-style-workflow/scripts/render_prompt.py

# Render prompt for a single area only
python .claude/skills/iterative-autocoder-style-workflow/scripts/render_prompt.py --only-title "Python Best Practices: Validation and Business Logic"
```

## Review contract

Reviewer must return JSON only:

```json
{
  "verdict": "ACCEPTED | DENIED",
  "reason": "short reason",
  "required_fixes": []
}
```
