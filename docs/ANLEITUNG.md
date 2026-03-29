# Anleitung: Claude Code Bundle einrichten

## Kurze Antwort: Ja, alles in den Projektordner

Genau wie bei der Codex CLI kommt **alles in den Root-Ordner des Projekts**, das du verbessern möchtest. Claude Code liest `CLAUDE.md` und `.claude/` automatisch beim Start.

---

## Schritt-für-Schritt-Anleitung

### Schritt 1: Bundle entpacken und in dein Projekt kopieren

```bash
unzip claude_code_autocoder_bundle.zip -d /pfad/zu/deinem/projekt/
```

Nach dem Entpacken sieht dein Projektordner so aus:

```
dein-projekt/
├── CLAUDE.md                          ← wird von Claude Code automatisch geladen
├── SOUL.md                            ← wird via Read-Order geladen
├── MEMORY.md                          ← wird via Read-Order geladen
├── .claude/
│   ├── agents/
│   │   ├── evaluator.md               ← Subagent: schreibgeschützt, analysiert
│   │   ├── implementer.md             ← Subagent: darf Dateien ändern
│   │   ├── reviewer.md                ← Subagent: schreibgeschützt, prüft Diff
│   │   ├── searcher.md                ← Subagent: sammelt Evidenz für Recherche
│   │   ├── verifier.md                ← Subagent: prüft Quellen / Claims
│   │   └── finalizer.md               ← Subagent: finalisiert nur verifizierte Claims
│   ├── skills/
│   │   ├── iterative-autocoder-style-workflow/
│   │   │   ├── SKILL.md
│   │   │   ├── review_schema.json
│   │   │   └── scripts/
│   │   │       ├── render_prompt.py
│   │   │       └── workflow_reporter.py
│   │   └── research-evidence-workflow/
│   │       ├── SKILL.md
│   │       └── verification_schema.json
│   └── profiles/
│       ├── task_profile_python_best_practices_iterative.json
│       ├── task_profile_research_evidence_iterative.json
│       └── iterations_manual.json
├── workflow/
│   ├── runtime/
│   ├── reports/
│   └── research/
├── docs_context/
│   └── profiles/full/
├── governance/
│   └── profiles/full/
└── docs/
    ├── ANLEITUNG.md                   ← diese Datei
    ├── INSTALL_AND_RUN.md
    └── PROMPT_EXAMPLES.md
```

> **Deine eigenen Projektdateien** (z.B. `src/`, `main.py`, `requirements.txt`) bleiben unverändert daneben liegen. Das Bundle stört deinen Code nicht.

---

### Schritt 2: Claude Code im Projektordner starten

```bash
cd dein-projekt
claude
```

Claude Code erkennt `CLAUDE.md` automatisch und lädt alle Subagenten aus `.claude/agents/`.

---

### Schritt 3 (optional): Aktive Bereiche prüfen

```bash
python .claude/skills/iterative-autocoder-style-workflow/scripts/workflow_reporter.py --write-report
```

Zeigt dir, welche Iterationen aktiv sind und ob git vorhanden ist.

---

### Schritt 4 (optional): Welche Best-Practices-Bereiche aktiv sind, ändern

Öffne `.claude/profiles/task_profile_python_best_practices_iterative.json` und setze `"enabled": true` für die Bereiche, die du analysieren willst.

Aktuell aktiviert: **Python Best Practices: Validation and Business Logic**

Alle verfügbaren Bereiche: Architecture, Data Model, Code Style, Type Safety, Error Handling, Logging, File I/O, Memory, Configuration, Validation, Security, Dependencies, Threading, Async I/O, Serialization, UI, Performance, Tests, Failure Injection, Packaging, Maintainability, Observability, Final Synthesis.

---

### Schritt 5: Workflow starten

Füge einen der Prompts aus `docs/PROMPT_EXAMPLES.md` in Claude Code ein.

**Vollautomatisch (empfohlen):**
```
Use the iterative-autocoder-style-workflow skill.

Load `.claude/profiles/task_profile_python_best_practices_iterative.json`.
Work only on enabled iterations.
If the project is not a git repo, initialize git locally, create a suitable `.gitignore`, and create a local baseline commit.
For each active area, explicitly invoke @agent-evaluator first, then @agent-implementer, then @agent-reviewer.
If review returns `DENIED`, feed `required_fixes` back to @agent-implementer and re-run @agent-reviewer until accepted or externally blocked.
After each accepted area, create a local commit.
Use local git only. Never push.
```

---

## Was passiert beim Workflow-Lauf

```
Claude Code
    │
    ├─► @agent-evaluator   (schreibgeschützt)
    │       analysiert Code, erstellt Plan + Checkliste
    │
    ├─► @agent-implementer (darf schreiben)
    │       setzt kleinste sinnvolle Änderung um
    │       macht lokalen git-Commit
    │
    └─► @agent-reviewer    (schreibgeschützt)
            prüft Diff → gibt ACCEPTED oder DENIED + JSON zurück
            bei DENIED: zurück zu implementer
```

Jeder Subagent läuft in **eigenem Kontext-Fenster** — kein Context-Überlauf.

---

## Unterschiede zu Codex CLI

| Aspekt | Codex CLI | Claude Code v2.1.76 |
|---|---|---|
| Haupt-Instruktionsdatei | `AGENTS.md` | `CLAUDE.md` |
| Subagent-Ordner | `.codex/agents/*.toml` | `.claude/agents/*.md` |
| Subagent-Aufruf | automatisch via Codex | `@agent-name` oder automatisch |
| Skill-Ordner | `.agents/skills/` | `.claude/skills/` |
| Profile | `.codex/profiles/` | `.claude/profiles/` |
| Config-Datei | `.codex/config.toml` | nicht nötig (CLAUDE.md übernimmt das) |

---

## Häufige Fragen

**Muss ich git installiert haben?**
Nein. Wenn kein `.git/` vorhanden ist, führt der Implementer automatisch `git init` aus.

**Werden meine Dateien verändert ohne Rückfrage?**
Ja — beim One-Shot-Modus. Wenn du lieber bestätigen willst, füge dem Prompt hinzu: `Ask for approval before each implementation step.`

**Kann ich mehrere Bereiche gleichzeitig aktivieren?**
Ja, aber sie werden nacheinander abgearbeitet (nicht parallel).

**Werden meine Änderungen gepusht?**
Niemals. Das Bundle macht ausschliesslich lokale Commits.


## Zusätzlicher Recherche-Workflow

Neu im Bundle ist ein separater Research-Track für Informationssuche:

- `@agent-searcher` sammelt Quellen und pflegt `workflow/research/evidence_pack.md`
- `@agent-verifier` prüft Claims und pflegt `workflow/research/claim_ledger.json`
- `@agent-finalizer` schreibt nur mit verifizierten Claims die Endfassung

Startdatei dafür ist `workflow/runtime/research_state.json`. Die passende Skill liegt unter `.claude/skills/research-evidence-workflow/`.
