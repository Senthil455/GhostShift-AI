# Testing and verification

GhostShift uses three layers: backend integration/unit tests, frontend unit tests, and Playwright end-to-end tests against a real backend and a real SQLite database. The tests use synthetic files in temporary directories. No test needs a hosted service.

## Commands

### Everything except browser E2E

```powershell
.\scripts\verify.ps1
```

```bash
./scripts/verify.sh
```

This runs:

1. `backend/gradlew test --no-daemon --console=plain`
2. `frontend/npm test` (Vitest)
3. `frontend/npm run build` (type-check plus Vite production build)

### Full suite including E2E

Start the backend first on port 8081 with an isolated data directory:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-25"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:GHOSTSHIFT_PORT = "8081"
$env:GHOSTSHIFT_DATA_DIR = "$env:TEMP\ghostshift-e2e-data"
cd backend
.\gradlew.bat run --no-daemon --console=plain
```

In another terminal:

```powershell
.\scripts\verify.ps1 -E2E -BackendPort 8081
```

The Playwright config starts Vite on `127.0.0.1:5173`, reuses an existing dev server if present, and proxies `/api` to the backend. It does **not** start the backend; global setup fails with a clear message if the API is not healthy.

Manual E2E invocation:

```powershell
cd frontend
$env:E2E_BASE_URL = "http://127.0.0.1:5173"
$env:E2E_API_URL = "http://127.0.0.1:8081"
npx playwright test
```

First-time browser install:

```powershell
cd frontend
npm run e2e:install
```

Optional token for E2E: set `E2E_API_TOKEN`; the helper sends it as a bearer token.

## What the suites cover

### Backend: 34 JUnit 5 tests

| Test class | Focus |
| --- | --- |
| `ApiIntegrationTest` | Health/settings, plan preview and undo, workflow approval before moves, recursive duplicate hashing with no deletion, settings validation and folder sanitization, reset confirmation, structured 404, seeded examples. |
| `ApiTokenIntegrationTest` | Health stays open while other routes require the configured token. |
| `DataResetIntegrationTest` | Activity reset deletes database rows but never touches a user file. |
| `RunStreamIntegrationTest` | SSE replays durable events with ids and closes after a terminal run. |
| `WorkflowValidatorTest` | Closed actions, unknown parameters, preview-before-move ordering, required directory input, duplicate step ids, closed category set. |
| `WorkflowContextSerdeTest` | Empty collections survive JSON round-trips (stable API shapes). |
| `PathGuardTest` | Traversal, absolute destinations, non-colliding names, nested destinations, Windows system path rejection. |
| `LogAnalyzerTest` | Severity counts, trailing newline, empty file. |
| `TextTest` | Truncation, filename sanitization, extension handling, signature normalization, deterministic hashing. |

Backend tests run with:

- `micronaut.server.port=-1` (random port)
- `ghostshift.data-dir=backend/build/test-data`
- `OLLAMA_ENDPOINT=http://127.0.0.1:9` and `OLLAMA_MODEL=test-no-model`

The unreachable endpoint is deliberate: the suite proves deterministic behavior and honest AI-unavailable fallbacks without depending on a local model. Live inference is a manual check (below), not a unit-test dependency.

### Frontend: 37 Vitest tests

| File | Focus |
| --- | --- |
| `src/api/client.spec.ts` | Versioned prefix, JSON content type, in-memory token, no-content responses, structured errors, request-id fallback, offline message, Markdown download. |
| `src/stores/session.spec.ts` | Bootstrap, theme precedence, offline degradation, settings save, local theme fallback, action catalogue caching. |
| `src/components/MarkdownLite.spec.ts` | Headings, lists, quotes, code, rules, link safety, raw HTML escaping, unclosed fences. |
| `src/components/StatusPill.spec.ts` | Humanized status, tone overrides, dot rendering (8 parameterized cases). |
| `src/lib/format.spec.ts` | Dates, bytes, durations, enum labels, folder names, pluralization, percentages, active-run detection, risk tones. |

### End-to-end: 10 Playwright tests

| Spec | Journey |
| --- | --- |
| `smoke.spec.ts` | First-visit onboarding, all ten nav sections, 404, theme persistence through the API, unsafe model endpoint rejection. |
| `sort.spec.ts` | Preview a plan, execute only after approval, undo verified moves. |
| `workflow.spec.ts` | Live workflow pauses at a human gate, resumes from the UI, records history. |
| `replay.spec.ts` | GhostReplay dry run from the UI, opens the run record, shows dry-run facts. |
| `capture.spec.ts` | Record allowlisted file steps and save a reviewable draft. |
| `insights.spec.ts` | GhostDev deterministic log analysis and GhostPulse exact duplicate evidence. |

E2E creates temporary folders and registers them through the API, then removes the registration and folder in `finally` blocks. Workflows created by tests are archived in cleanup. The tests mutate onboarding/theme and may leave activity rows; use an isolated `GHOSTSHIFT_DATA_DIR` and clear it when you want a pristine environment.

## Manual verification checklist

These are not replaced by the automated suite.

### Local model, real inference

1. Start Ollama and pull the configured model: `ollama pull gemma2:2b`.
2. `GET /api/v1/ai/status?refresh=true` returns `"status":"READY"` and `"modelAvailable":true`.
3. `POST /api/v1/settings/model/test` returns `ok: true` with a non-empty sample response and latency.
4. Analyze the demo log with AI on. Confirm the report is labelled as AI-assisted and contains no fabricated certainty.
5. Draft a workflow in GhostBrain. Confirm it is only a proposal and that saving is explicit.

### SSE

```bash
curl -N "http://127.0.0.1:8081/api/v1/runs/<runId>/events/stream?after=0"
```

Confirm events replay, each frame has an `id`, and the stream closes after the run reaches `COMPLETED`, `PARTIALLY_COMPLETED`, `FAILED`, or `CANCELLED`. The integration test also covers this.

### Optional API token

1. Start the backend with `GHOSTSHIFT_API_TOKEN=local-test-token`.
2. `/api/v1/health` succeeds without a token.
3. `/api/v1/settings` returns `401` without one.
4. The same request with `Authorization: Bearer local-test-token` succeeds.
5. Paste the token into Settings and confirm the UI loads; refresh the tab and confirm the token must be entered again.

### Filesystem safety

1. Register a read-only folder and confirm GhostSort refuses to organize it.
2. Try a destination collision and confirm a `(1)` name is used, not an overwrite.
3. Modify a file after preview and confirm execution refuses it.
4. Move a file, modify it, and confirm undo refuses.
5. Try a symlink inside a registered folder and confirm it is skipped/refused.
6. Confirm no operation deletes a user file.

### UI and accessibility

1. Check all ten sections plus Welcome at 1440 px and at 390 px wide. No horizontal overflow.
2. Tab through the sidebar, dialogs, forms, and approval controls. Focus is visible; Escape closes dialogs.
3. Check dark and light themes. Text contrast and status tones remain legible.
4. Confirm loading, empty, error, and offline states are distinct.
5. Confirm destructive actions use a confirmation dialog and an explicit word/choice.

## Current verified results

Results from the build environment for this version:

| Check | Result |
| --- | --- |
| Backend JUnit suite | 34/34 passed (rerun after the SSE change) |
| Frontend Vitest suite | 37/37 passed |
| Frontend type-check and production build | Passed |
| Playwright Chromium suite | 10/10 passed against backend `127.0.0.1:8081` (rerun after the SSE change) |
| SSE with a real workflow run | Verified manually: frames carried ids, replayed from `after=0`, and the stream closed after `COMPLETED` |
| Real Ollama inference | Manual check performed with `gemma2:2b` (13.4 s, non-empty sample) |
| `docker compose config` | Passed |
| Docker image build/run | **Not verified**: no Docker daemon available |

See the final project report for the exact commands and any environment-specific caveats. Test counts describe this revision; update them when tests change.

## Known gaps

- **No browser/screen recording tests** because the feature does not exist. The API rejects it explicitly.
- **SSE is not used by the Vue UI.** The endpoint is tested directly; the UI polls by design.
- **Docker runtime is untested.** Compose configuration was parsed, but no images were built.
- **No load or multi-user testing.** SQLite and a two-thread worker are intentional single-user choices.
- **AI output is non-deterministic.** Tests assert schema validation and fallbacks, not exact prose.
- **Windows and Linux path handling differ.** PathGuard has a Windows-specific system-directory test; Unix system paths are implemented but not exercised on Windows.
