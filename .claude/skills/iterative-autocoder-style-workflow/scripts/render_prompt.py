#!/usr/bin/env python3
import argparse
import json
from pathlib import Path


DEFAULT_PROFILE = ".claude/profiles/task_profile_python_best_practices_iterative.json"
DEFAULT_APP_STATE = "workflow/runtime/app_state.json"


def _resolve_path(repo_root: Path, raw_path: str) -> Path:
    path = Path(raw_path)
    if not path.is_absolute():
        path = repo_root / path
    return path.resolve()


def resolve_profile_path(repo_root: Path, app_state: str, profile_override: str) -> Path:
    if profile_override:
        return _resolve_path(repo_root, profile_override)

    app_state_path = _resolve_path(repo_root, app_state)
    if app_state_path.exists():
        try:
            app_state_data = json.loads(app_state_path.read_text(encoding="utf-8"))
            profile_path = app_state_data.get("profile_path")
            if isinstance(profile_path, str) and profile_path.strip():
                return _resolve_path(repo_root, profile_path)
        except Exception:
            pass

    return _resolve_path(repo_root, DEFAULT_PROFILE)


def to_display_path(path: Path, repo_root: Path) -> str:
    try:
        return path.relative_to(repo_root).as_posix()
    except ValueError:
        return path.as_posix()


def main() -> int:
    parser = argparse.ArgumentParser(description="Render a Claude orchestration prompt from a task profile.")
    parser.add_argument("--repo-root", default=".")
    parser.add_argument("--app-state", default=DEFAULT_APP_STATE)
    parser.add_argument(
        "--profile",
        default=None,
        help="Optional explicit profile path. If omitted, profile_path is resolved from app_state.json.",
    )
    parser.add_argument("--only-title", default=None, help="Limit to a single iteration title.")
    args = parser.parse_args()

    repo_root = Path(args.repo_root).resolve()
    profile_path = resolve_profile_path(repo_root=repo_root, app_state=args.app_state, profile_override=args.profile)
    data = json.loads(profile_path.read_text(encoding="utf-8"))
    iterations = [it for it in data.get("iterations", []) if it.get("enabled")]
    if args.only_title:
        iterations = [it for it in iterations if it.get("title") == args.only_title]

    lines = []
    lines.append("Use the iterative-autocoder-style-workflow skill.")
    lines.append("")
    lines.append(f"Load `{to_display_path(profile_path, repo_root)}`.")
    if args.only_title:
        lines.append("Ignore other areas and work only on:")
        lines.append(args.only_title)
        lines.append("")
        lines.append("If the project is not a git repo, initialize git locally, create a suitable `.gitignore`, and create a local baseline commit.")
        lines.append("Spawn `evaluator`, then `implementer`, then `reviewer`.")
        lines.append("The evaluator and reviewer must stay read-only.")
        lines.append("The implementer may only change code for this area.")
        lines.append("Run the lightest credible validation for touched files.")
        lines.append("If review returns `DENIED`, pass `required_fixes` back to `implementer` and re-run `reviewer`.")
        lines.append("After acceptance, create a local commit for this area.")
    else:
        lines.append("Work only on enabled iterations.")
        lines.append("If the project is not a git repo, initialize git locally, create a suitable `.gitignore`, and create a local baseline commit.")
        lines.append("For each active area, explicitly spawn `evaluator` first, then `implementer`, then `reviewer`.")
        lines.append("Keep scope limited to the active area, respect `implementation_mode`, run targeted validation, and separate blockers from suggestions.")
        lines.append("If review returns `DENIED`, feed `required_fixes` back to `implementer` and re-run `reviewer` until accepted or externally blocked.")
        lines.append("After each accepted area, create a local commit.")
    lines.append("Use local git only. Never push.")
    if iterations:
        lines.append("")
        lines.append("Active iterations:")
        for idx, it in enumerate(iterations, start=1):
            lines.append(f"{idx}. {it.get('title')} [{it.get('implementation_mode', 'unknown')}]")
    print("\n".join(lines))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
