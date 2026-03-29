# Start Policy

This bundle starts in **full automatic workflow** mode.

Default start behavior:
- inspect the task profile
- detect enabled iterations
- ensure local git readiness
- run evaluator, implementer, reviewer in sequence
- retry implementation/review when review returns `DENIED`
- create local commits for accepted work
- continue through the active iteration list

No intermediate user approval is required for:
- code/file changes inside the active scope
- local `git init`
- local `.gitignore` creation/update
- local staging and local commits

The workflow should still stop or report clearly when blocked by external constraints such as missing tools, failing commands that need user intervention, permissions, or ambiguous repository state.
