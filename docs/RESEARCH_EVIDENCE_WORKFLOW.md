# Research Evidence Workflow

This bundle now includes a second workflow track for information gathering tasks.

It runs as a strict three-role loop:

1. **Search** via `@agent-searcher`
2. **Verification** via `@agent-verifier`
3. **Finalization** via `@agent-finalizer`

The workflow is designed to reduce unsupported claims by keeping persistent research artifacts:

- `workflow/research/evidence_pack.md`
- `workflow/research/claim_ledger.json`

## Why use this workflow

Use the research-evidence workflow when the task is primarily about:
- gathering information,
- comparing sources,
- resolving contradictions,
- producing a source-aware final summary.

Use the existing iterative-autocoder workflow when the task is primarily about repository or code changes.

## Files involved

### Agents
- `.claude/agents/searcher.md`
- `.claude/agents/verifier.md`
- `.claude/agents/finalizer.md`

### Skill
- `.claude/skills/research-evidence-workflow/SKILL.md`
- `.claude/skills/research-evidence-workflow/verification_schema.json`

### Profile
- `.claude/profiles/task_profile_research_evidence_iterative.json`

### Runtime / artifacts
- `workflow/runtime/research_state.json`
- `workflow/research/evidence_pack.md`
- `workflow/research/claim_ledger.json`
- `workflow/reports/research_verification_report.json`
- `workflow/reports/research_final_output.md`

## Artifact responsibilities

### Evidence pack
Stores source notes, extracted findings, reliability notes, and open gaps.

### Claim ledger
Stores claims, status, linked sources, confidence, and whether a claim was used in the final answer.

Recommended status values:
- `unverified`
- `verified`
- `rejected`
- `needs_more_evidence`

## Default loop

1. Read `workflow/runtime/research_state.json`
2. Load the active research profile
3. Work on the first enabled research iteration
4. Run `@agent-searcher`
5. Run `@agent-verifier`
6. If verdict is `DENIED`, feed `required_fixes` back to `@agent-searcher`
7. Repeat until verification is accepted or blocked by missing evidence
8. Run `@agent-finalizer`
9. Write optional reports under `workflow/reports/`

## Scope rules

- Searcher may gather evidence and update research artifacts.
- Verifier decides claim usability.
- Finalizer may only use verified claims.
- Unverified or rejected claims must never appear as settled facts in the final synthesis.
