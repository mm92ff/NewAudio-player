# Python Docs (Full)

## Module structure

- Keep module responsibilities single and focused.
- Avoid circular imports.
- Keep entry points thin; no business logic in top-level CLI or app bootstrap code.
- Isolate third-party adapters behind narrow boundaries.

## Error handling

- No bare `except:` or `except Exception: pass`.
- Preserve exception chains with `raise ... from ...`.
- Clean up resources with `with` blocks or `finally`.

## Type safety

- Public functions should have complete parameter and return annotations.
- Prefer `dataclass`, `TypedDict`, or pydantic-style structured models instead of untyped dictionaries for stable interfaces.
- Narrow `None` values before use.

## File I/O

- Prefer atomic writes for critical files.
- Use `pathlib.Path`.
- Specify `encoding='utf-8'`.
- Open file handles in `with` blocks.

## Testing

- Use targeted pytest tests for touched behavior.
- Cover boundary values and error paths where practical.
- Prefer deterministic tests.

## Security

- Do not pass user-controlled input to `eval`, `exec`, or `shell=True`.
- Use parameterized SQL queries.
- Avoid leaking secrets in logs or exceptions.

## Performance

- Profile before optimizing.
- Avoid materializing large collections when streaming/generator patterns fit.
- Prefer bounded caches.
