#!/usr/bin/env python3
import argparse
import json
import subprocess
from datetime import datetime, timezone
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


def git_status(repo_root: Path) -> dict:
    git_dir = repo_root / ".git"
    status = {
        "has_git_dir": git_dir.exists(),
        "git_available": False,
        "branch": None,
        "clean": None,
        "gitignore_exists": (repo_root / ".gitignore").exists(),
    }
    try:
        completed = subprocess.run(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"],
            cwd=repo_root,
            capture_output=True,
            text=True,
            check=True,
        )
        status["git_available"] = True
        status["branch"] = completed.stdout.strip()
        dirty = subprocess.run(
            ["git", "status", "--porcelain"],
            cwd=repo_root,
            capture_output=True,
            text=True,
            check=True,
        )
        status["clean"] = (dirty.stdout.strip() == "")
    except Exception:
        pass
    return status


def bootstrap_plan(git: dict) -> dict:
    actions = []
    notes = []
    if not git.get("has_git_dir"):
        actions.extend([
            "git init",
            "create suitable .gitignore",
            "inspect git status",
            "create local baseline/bootstrap commit",
        ])
        notes.append("Repository is not yet initialized as git; bootstrap will run automatically.")
    elif not git.get("gitignore_exists"):
        actions.append("create suitable .gitignore")
        notes.append(".gitignore is missing; it should be added before broader iteration work.")
    else:
        notes.append("Local git bootstrap already looks present.")
    return {
        "bootstrap_needed": len(actions) > 0,
        "planned_actions": actions,
        "notes": notes,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Summarize iterative workflow git readiness.")
    parser.add_argument("--repo-root", default=".")
    parser.add_argument("--app-state", default=DEFAULT_APP_STATE)
    parser.add_argument(
        "--profile",
        default=None,
        help="Optional explicit profile path. If omitted, profile_path is resolved from app_state.json.",
    )
    parser.add_argument("--write-report", action="store_true")
    args = parser.parse_args()

    repo_root = Path(args.repo_root).resolve()
    profile_path = resolve_profile_path(repo_root=repo_root, app_state=args.app_state, profile_override=args.profile)
    data = json.loads(profile_path.read_text(encoding="utf-8"))
    code_language = data.get("code_language")
    enabled = [it for it in data.get("iterations", []) if it.get("enabled")]
    git = git_status(repo_root)
    bootstrap = bootstrap_plan(git)
    report = {
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "repo_root": str(repo_root),
        "profile_path": str(profile_path),
        "code_language": code_language,
        "enabled_count": len(enabled),
        "enabled_titles": [it.get("title") for it in enabled],
        "git": git,
        "bootstrap": bootstrap,
        "suggested_start_mode": "full automatic one-shot",
    }

    print(json.dumps(report, indent=2))
    if args.write_report:
        out_dir = repo_root / "workflow" / "reports"
        out_dir.mkdir(parents=True, exist_ok=True)
        out_file = out_dir / "bootstrap_status.json"
        out_file.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
