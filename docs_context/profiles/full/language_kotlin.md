# Kotlin/Android Docs (Full)

## Architecture

- Keep UI, domain, and data layers cleanly separated (e.g. Compose/Fragment → ViewModel → UseCase/Repository → DataSource).
- Entry points (Activity, Fragment, Composable) stay thin; no business logic in UI callbacks.
- Avoid circular package dependencies.
- Full multi-module setup is optional for small apps; layered packages inside one module are acceptable.

## Coroutines and concurrency

- No `GlobalScope` usage in production code.
- Use lifecycle-aware scopes: `viewModelScope`, `lifecycleScope`, or custom supervised scopes.
- Respect structured concurrency: parent cancellation must propagate to children.
- Long-running or blocking work runs on `Dispatchers.IO`; UI updates happen on `Dispatchers.Main` only.
- Flow collectors must be lifecycle-aware (`collectWithLifecycle` / `repeatOnLifecycle`) to avoid leaks.

## Null safety and type system

- Avoid `!!` in production paths; prefer safe calls, `?: return`, or `requireNotNull` with a message.
- Narrow nullable types before use.
- Prefer sealed classes or sealed interfaces for exhaustive state and result modeling.
- Avoid platform types from Java interop in public APIs; annotate or wrap them.

## State management

- UI state is represented as a single `StateFlow<UiState>` per screen.
- One-time events (navigation, toasts) are kept separate from persistent state (e.g. `SharedFlow` or `Channel`).
- State must survive configuration changes via `ViewModel`; use `SavedStateHandle` where process death matters.
- No direct business logic in `onClickListener`, Composable lambdas, or `Fragment` lifecycle callbacks.

## Error handling

- Failures surface with clear user-visible messages; exceptions are not silently swallowed.
- Use `Result<T>` or sealed `Either`-style wrappers for recoverable errors in domain/data layers.
- Distinguish recoverable errors (show retry UI) from fatal errors (log and crash-report).
- Network and I/O calls use explicit timeouts; malformed responses are handled safely, not crash-path.

## Testing

- Unit tests cover ViewModel logic, use-cases, and repository behavior using fakes/mocks.
- Tests are deterministic: use `TestCoroutineDispatcher` / `UnconfinedTestDispatcher` and avoid `Thread.sleep`.
- At least smoke-level UI or integration coverage exists for the critical user flow.
- `./gradlew test` must pass before a patch is considered done.

## Security and permissions

- Least-privilege permissions in `AndroidManifest.xml`; justify each permission explicitly.
- Exported components (`Activity`, `BroadcastReceiver`, `ContentProvider`) are intentional and protected.
- No secrets or API keys hardcoded in source or `BuildConfig`; use secure storage or server-side delivery.
- No sensitive data in logs, plain `SharedPreferences`, or implicit intents.

## Performance

- Avoid unnecessary recompositions: use `remember`, stable keys, and `derivedStateOf` appropriately.
- List rendering uses `LazyColumn`/`LazyRow` with stable item keys.
- No blocking work or expensive computation on the Main thread.
- Profile before optimizing; do not guess at bottlenecks.

## Logging

- No `println` in production code; use `Log.d/i/w/e` with a consistent tag.
- Log levels are meaningful: debug for development noise, warning/error for actionable issues.
- No PII, secrets, or user content in logs.

## Build and tooling

- Key commands: `./gradlew build`, `./gradlew lint`, `./gradlew test`, `./gradlew connectedDebugAndroidTest`
- Lint warnings in security and correctness categories are treated as errors.
- Detekt / Ktlint configuration is respected; do not suppress rules without justification.
