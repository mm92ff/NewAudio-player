# Diff and Review Contract

## Evaluator

Returns markdown with:
- Summary
- Plan
- Acceptance Checklist
- Risks

## Implementer

Returns markdown with:
- Changed Files
- What Was Fixed
- Checks Run
- Open Risks

## Reviewer

Returns strict JSON only:

```json
{
  "verdict": "ACCEPTED | DENIED",
  "reason": "short reason",
  "required_fixes": []
}
```

## Retry rule

If reviewer returns `DENIED`, only the implementer may make follow-up changes.
The reviewer re-runs after the implementer addresses the listed `required_fixes`.
