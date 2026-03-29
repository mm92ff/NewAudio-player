---
name: research-evidence-workflow
description: Use this skill when you want a three-stage research workflow with one searcher, one verifier, and one finalizer per active iteration from a JSON research profile, with an evidence pack and claim ledger.
---

Use this skill for information gathering tasks that should follow a strict evidence loop:

1. read `workflow/runtime/research_state.json` to resolve the active `profile_path`,
2. load the research profile from that path,
3. determine enabled iterations,
4. initialize or refresh `workflow/research/evidence_pack.md` and `workflow/research/claim_ledger.json` when needed,
5. work on one active iteration at a time,
6. explicitly invoke `@agent-searcher`,
7. explicitly invoke `@agent-verifier`,
8. if verification returns `DENIED`, update `workflow/reports/research_verification_report.json`, feed `required_fixes` back to `@agent-searcher`, and re-run `@agent-verifier`,
9. after verification returns `ACCEPTED`, invoke `@agent-finalizer`,
10. update `workflow/runtime/research_state.json` and optional reports,
11. continue with the next enabled research iteration.

## Inputs expected by this skill

- `workflow/runtime/research_state.json` with a valid `profile_path` pointing to a research profile under `.claude/profiles/`
- `workflow/research/evidence_pack.md` and `workflow/research/claim_ledger.json` or permission to create them
- Optional instruction to limit execution to one specific research area
- Optional instruction to persist final output under `workflow/reports/`

## Required behavior

- Resolve the active profile from `research_state.json` at the start of every run.
- Respect `enabled` in the research profile.
- Keep scope limited to the active iteration.
- Treat search and verification as evidence-first phases, not answer-writing phases.
- Treat finalization as synthesis-only and allow only verified claims.
- If verification denies, keep the fix loop narrow and retry until accepted or clearly blocked by missing evidence.
- Preserve an audit trail through the evidence pack and claim ledger.
- Never silently upgrade an unverified claim into a final conclusion.

## Artifacts maintained by this skill

- `workflow/research/evidence_pack.md` — source notes, extracted findings, gaps
- `workflow/research/claim_ledger.json` — claim registry with statuses and linked sources
- `workflow/reports/research_verification_report.json` — latest verifier JSON snapshot
- `workflow/reports/research_final_output.md` — optional final synthesized output
- `workflow/runtime/research_state.json` — active profile and last research run metadata

## Recommended orchestration pattern

```text
Use the research-evidence-workflow skill.

Read `workflow/runtime/research_state.json` and resolve `profile_path`.
Load that profile from `.claude/profiles/...`.
Work only on enabled research iterations.
For each active area, explicitly invoke @agent-searcher first, then @agent-verifier.
If verification returns `DENIED`, pass `required_fixes` back to @agent-searcher and re-run @agent-verifier.
Only after verification returns `ACCEPTED`, invoke @agent-finalizer.
Update the evidence pack, claim ledger, and research reports as you go.
Never use unverified claims in the final output.
```
