# Search and Verification Contract

## Searcher

Returns markdown with:
- Search Summary
- Sources Found
- Candidate Claims
- Open Gaps

Also updates:
- `workflow/research/evidence_pack.md`
- `workflow/research/claim_ledger.json`

## Verifier

Returns strict JSON only:

```json
{
  "verdict": "ACCEPTED | DENIED",
  "reason": "short concrete reason",
  "required_fixes": [],
  "claim_results": [
    {
      "claim_id": "C-001",
      "status": "verified | rejected | needs_more_evidence | unverified",
      "confidence": "high | medium | low",
      "notes": "short concrete note"
    }
  ]
}
```

Also updates:
- `workflow/research/claim_ledger.json`
- optionally `workflow/reports/research_verification_report.json`

## Finalizer

Returns markdown with:
- Final Output
- Key Findings
- Caveats
- Source Notes

May also update:
- `workflow/reports/research_final_output.md`

## Retry rule

If verifier returns `DENIED`, only the searcher should gather the requested additional evidence or clean up the ledger. The verifier then re-runs.

## Non-negotiable rule

The finalizer may only use claims marked `verified`.
