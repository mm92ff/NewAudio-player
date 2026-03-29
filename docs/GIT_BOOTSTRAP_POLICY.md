# Git Bootstrap Policy

This setup is intentionally automation-first for **local** git only.

## Default behavior

Without additional prompting, the workflow should:
- run `git init` if the project is not yet a git repo
- create or update a project-appropriate `.gitignore` when needed
- inspect `git status`
- create a local baseline/bootstrap commit when practical
- create local commits for accepted iteration work
- never push
- never add or modify remotes

## Why

The evaluator/implementer/reviewer loop is easier to control with clean diffs, reversible checkpoints, and clear per-iteration commits.

## Recommended automatic sequence

1. detect whether `.git/` exists
2. if missing, run `git init`
3. create or update `.gitignore` if needed
4. inspect `git status`
5. create a local baseline/bootstrap commit if that improves the upcoming diff/review loop
6. run the iterative workflow
7. after each accepted iteration, create a local commit

## Safety boundaries

Allowed automatically:
- local `git init`
- local `.gitignore` creation/update
- local `git add`
- local `git commit`
- local `git status` / `git diff`

Not allowed automatically:
- `git push`
- adding or changing remotes
- force pushes
- destructive cleanup such as `reset --hard` or `clean -fd`
- branch deletion

## Commit guidance

Prefer:
- one bootstrap/baseline commit when needed
- one local commit per accepted iteration

Example commit messages:
- `chore: initialize local git workflow`
- `fix(validation-and-business-logic): tighten domain validation checks`
- `refactor(type-safety-and-api-contracts): narrow response typing`
