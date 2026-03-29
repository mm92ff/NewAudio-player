# Core Docs (Full)

This file complements `AGENTS.md`.
Use it for output and validation expectations, not for repository scope rules.

## Output expectations

Implementation output must include:
- changed files with the reason each was modified,
- behavior added, changed, or preserved,
- checks run and pass/fail status,
- unresolved risks or follow-up items.

Review output must be strict JSON only:
- `verdict`: `ACCEPTED` or `DENIED`
- `reason`: short, concrete explanation
- `required_fixes`: actionable list, empty when accepted

## Validation

- Run required checks before declaring success when feasible.
- Run optional checks when they are cheap and relevant.
- If a check cannot run, state the blocker exactly.
- Check results are authoritative; do not override failed checks with optimism.

## Change quality

- Prefer small, intention-revealing diffs.
- Avoid hidden side effects.
- Preserve existing behavior unless the active iteration explicitly requires a behavior change.
- Tie conclusions to files, symbols, commands, or real test output whenever possible.
