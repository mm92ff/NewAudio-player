# Prompt Examples — Claude Code Edition

## Vollautomatischer Start für alle aktivierten Bereiche

```text
Use the iterative-autocoder-style-workflow skill.

Read `workflow/runtime/app_state.json` and resolve `profile_path`.
Load that profile from `.claude/profiles/...`.
Work only on enabled iterations.
If the project is not a git repo, initialize git locally, create a suitable `.gitignore`, and create a local baseline commit.
For each active area, explicitly invoke @agent-evaluator first, then @agent-implementer, then @agent-reviewer.
Keep scope limited to the active area, respect `implementation_mode`, run targeted validation, and separate blockers from suggestions.
If review returns `DENIED`, feed `required_fixes` back to @agent-implementer and re-run @agent-reviewer until accepted or externally blocked.
After each accepted area, create a local commit.
Use local git only. Never push.
Execute all iterations autonomously.
```

Optional explizit:
- Python: `Load .claude/profiles/task_profile_python_desktop_tool.json.`
- Python: `Load .claude/profiles/task_profile_python_best_practices_iterative.json.`
- Kotlin (Android): `Load .claude/profiles/task_profile_kotlin_android_studio_iterative.json.`
- Kotlin (Simple App): `Load .claude/profiles/task_profile_kotlin_android_studio_simple_app.json.`





## Vollautomatischer Start nur für den aktuell gewählten Bereich

```text
Use the iterative-autocoder-style-workflow skill.

Read `workflow/runtime/app_state.json` and resolve `profile_path`.
Load that profile from `.claude/profiles/...`.
Ignore other areas and work only on:
<INSERT ITERATION TITLE HERE>

If the project is not a git repo, initialize git locally, create a suitable `.gitignore`, and create a local baseline commit.
Invoke @agent-evaluator, then @agent-implementer, then @agent-reviewer.
The evaluator and reviewer must stay read-only.
The implementer may only change code for this area.
Run the lightest credible validation for touched files.
If review returns `DENIED`, pass `required_fixes` back to @agent-implementer and re-run @agent-reviewer.
After acceptance, create a local commit for this area.
Use local git only. Never push.
```

## Nur Status/Diagnose vor dem Start

```text
Use the iterative-autocoder-style-workflow skill.

Read `workflow/runtime/app_state.json` and resolve `profile_path`.
Load that profile from `.claude/profiles/...`.
Inspect enabled iterations and current git readiness.
Do not widen scope and do not push anything.
Return a concise execution summary and then continue with the normal automated flow unless the repository is externally blocked.
```

## Manueller Subagent-Aufruf (einzeln)

Evaluator direkt aufrufen:
```text
@agent-evaluator Analyze the active iteration "<INSERT ITERATION TITLE HERE>" from `<INSERT PROFILE PATH HERE>`. Stay read-only.
```

Implementer nach Evaluator aufrufen:
```text
@agent-implementer Implement the changes for "<INSERT ITERATION TITLE HERE>" based on the evaluator's report above. Use local git only.
```

Reviewer nach Implementer aufrufen:
```text
@agent-reviewer Review the diff from the implementer above. Return strict JSON verdict only.
```


## Recherche-Workflow für informationsbasierte Aufgaben

```text
Use the research-evidence-workflow skill.

Read `workflow/runtime/research_state.json` and resolve `profile_path`.
Load that profile from `.claude/profiles/...`.
Work only on enabled research iterations.
For each active research area, explicitly invoke @agent-searcher first, then @agent-verifier.
If verification returns `DENIED`, pass `required_fixes` back to @agent-searcher and re-run @agent-verifier.
Only after verification returns `ACCEPTED`, invoke @agent-finalizer.
Keep the evidence pack and claim ledger up to date.
Never use unverified claims in the final output.
```

## Recherche nur für einen Bereich

```text
Use the research-evidence-workflow skill.

Read `workflow/runtime/research_state.json` and resolve `profile_path`.
Load that profile from `.claude/profiles/...`.
Ignore other research areas and work only on:
<INSERT RESEARCH ITERATION TITLE HERE>

Invoke @agent-searcher, then @agent-verifier.
If verifier returns `DENIED`, feed `required_fixes` back to @agent-searcher and re-run @agent-verifier.
Invoke @agent-finalizer only after accepted verification.
Persist evidence in `workflow/research/evidence_pack.md` and claim status in `workflow/research/claim_ledger.json`.
```
