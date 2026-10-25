# GhostShift AI

A local-first automation studio for the files and logs already on your machine. GhostShift scans a folder you register, proposes a plan, validates it in deterministic code, asks you to approve the risky parts, executes only that plan, and records what happened.

It is a Java 25 / Micronaut backend, a Vue 3 single-page app, a SQLite database, and an optional Ollama runtime. There is no account, no cloud control plane, no telemetry, and no hosted model provider.

> **Scope note:** this repository is a working v1.0.0 implementation. Browser/screen recording is not implemented; GhostCapture records allowlisted file-action steps. Docker packaging is included but was not runtime-tested in the build environment. See [Honest limitations](#honest-limitations).

## What it does

| Module | What is real today |
| --- | --- |
| **GhostSort** | Bounded folder scan, deterministic filename classification, optional local-model classification, destination preview, explicit approve/reject per file, guarded moves, and verified undo. |
| **GhostDev** | Read-only log and text analysis. Deterministic error grouping and counts always run first; the local model may add a clearly labelled interpretation. Exports Markdown reports. |
| **GhostCapture** | Records an allowlisted sequence of file actions, lets you pause, resume, edit, reorder, and delete steps, then saves the result as a draft workflow. |
| **GhostReplay** | Starts a saved workflow as a dry run or a live run, shows inputs and the action catalogue, pauses at approval gates, and links to the run record. |
| **GhostBrain** | Checks the Ollama endpoint, lists installed models, tests a model, and drafts workflows from a prompt. Drafts are validated and never auto-executed. |
| **GhostPulse** | Dashboard, estimated time savings with visible assumptions, exact duplicate evidence (SHA-256), local system facts, and open signals. |

Cross-cutting features: approvals inbox, run history, activity log, report library, SSE or polling run events, onboarding, dark/light themes, optional local API token, demo data, and a confirmed local-data reset that never touches your files.

## Safety model

The design rule is: **AI suggests, deterministic code validates, the user authorizes, the engine executes, history records.**

- Workflows use a closed action registry. A model cannot add an action, permission, or destination.
- File destinations are computed by application code inside an app-managed organizer folder. No model output is used as a path.
- File moves require a backend approval gate. The gate is enforced in the executor, not only hidden in the UI.
- GhostShift never deletes user files. It never silently overwrites: moves use non-colliding names and fail on races.
- Reads and writes are bounded to registered folders. Symlinks, system directories, and paths that resolve outside the boundary are refused.
- A move records size, SHA-256, source, and destination. Undo refuses if the file changed after the move.
- Log content and filenames are treated as untrusted data. Prompts instruct the model not to follow embedded instructions, and model output is parsed into closed schemas.
- API tokens are optional, compared in constant time, and never stored by the app. The browser keeps a pasted token in memory only.
- Errors return one JSON shape with a request id. Stack traces stay in local logs.

Full details and residual risks are in [docs/security.md](docs/security.md).

## Stack

| Layer | Choice |
| --- | --- |
| Backend | Java 25, Micronaut 5.2, Gradle 9.8, Netty, Micronaut Serde |
| API | REST under `/api/v1`, optional bearer token, Server-Sent Events for run events |
| Database | SQLite in WAL mode, direct JDBC, short-lived connections, versioned SQL migrations |
| Validation/tests | Jakarta Bean Validation, JUnit 5, Micronaut Test |
| Frontend | Vue 3, TypeScript, Vite 7, Vue Router, Pinia, custom CSS design system |
| AI runtime | Ollama with an open-weight model, default `gemma2:2b` |
| E2E | Playwright (Chromium) |

## Prerequisites

- **Java 25** (Temurin recommended). The Gradle build targets Java 25.
- **Node.js 20.19+ or 22.12+** and npm (Vite 7 requirement).
- **Ollama** (optional). Without it, every deterministic feature still works; AI actions return an honest "model unavailable" result instead of fabricated output.

On Windows, this repository was verified with a portable Temurin JDK at `.tools/jdk25/jdk-25.0.4.1+1`. That folder is git-ignored.

## Quick start

### Windows

```powershell
.\scripts\dev.ps1
```

The script finds the bundled JDK or `JAVA_HOME`, starts the backend on `http://127.0.0.1:8081`, waits for health, then starts Vite on `http://127.0.0.1:5173`. Press `Ctrl+C` to stop both.

### macOS / Linux

```bash
# Terminal 1
cd backend
JAVA_HOME=/path/to/jdk-25 ./gradlew run --no-daemon --console=plain

# Terminal 2
cd frontend
npm install
VITE_API_PROXY_TARGET=http://127.0.0.1:8080 npm run dev
```

Open the Vite URL. The dev server proxies `/api` to the backend, so the browser uses one origin.

### First visit

1. The Welcome screen opens. Choose **Enter the workspace** or **Create demo workspace**.
2. The demo option creates synthetic files under `<data-dir>/demo/inbox` and seeds three example workflows. It never reads personal folders.
3. Register a folder in **Settings -> Registered folders**. Read-only registration is supported.
4. Use **GhostSort** for a preview-first cleanup, **GhostDev** for a log, or **GhostReplay** for the example workflows.

### Ollama

```bash
ollama serve
ollama pull gemma2:2b
```

Then open **GhostBrain** or **Settings -> GhostBrain model**, test the connection, and save. The default endpoint is `http://localhost:11434`. A different model can be entered if it is installed locally. GhostShift does not download models or call a hosted provider.

## Configuration

The backend reads these environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `GHOSTSHIFT_HOST` | `127.0.0.1` | Bind address. Keep loopback unless you add a reverse proxy and token. |
| `GHOSTSHIFT_PORT` | `8080` | HTTP port. Use `8081` if `8080` is occupied. |
| `GHOSTSHIFT_DATA_DIR` | `./data` | SQLite database, generated reports, and synthetic demo files. |
| `GHOSTSHIFT_API_TOKEN` | empty | When set, every `/api/v1` route except `/health` requires a bearer token. |
| `OLLAMA_ENDPOINT` | `http://localhost:11434` | Default model endpoint. Runtime settings in SQLite can override it. |
| `OLLAMA_MODEL` | `gemma2:2b` | Default model name. |
| `GHOSTSHIFT_API_PORT` | `8080` | Docker Compose host port for the API. |
| `GHOSTSHIFT_WEB_PORT` | `3000` | Docker Compose host port for the web UI. |

Frontend build/dev variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `VITE_API_PROXY_TARGET` | `http://127.0.0.1:8080` | Vite dev proxy target for `/api`. |

Copy `.env.example` to `.env` for Docker Compose. The Java app reads environment variables directly.

### API token

Set `GHOSTSHIFT_API_TOKEN` before starting the backend. Paste the same value into **Settings -> Local access token** in the UI. It is held in memory for the current tab and is not written to localStorage or SQLite. The health endpoint stays open so the UI can show a useful offline state.

## Docker

Packaging is provided as a multi-stage backend image, an nginx-served frontend image, and a Compose file with a named data volume. Ports bind to loopback only.

```bash
cp .env.example .env
docker compose up --build
# UI:  http://127.0.0.1:3000
# API: http://127.0.0.1:8080
```

The backend container reaches a host Ollama through `host.docker.internal:11434` by default. Register folders that are visible inside the container, or use the direct local run for desktop folders.

`docker compose config` was validated. **The images were not built or run** in the verification environment because no Docker daemon was available. Treat Docker as unverified until you run it yourself.

## Verification

```powershell
# Unit tests + frontend production build
.\scripts\verify.ps1

# The same, plus Playwright against an already-running backend on 8081
.\scripts\verify.ps1 -E2E
```

```bash
E2E=1 ./scripts/verify.sh
```

For manual test commands, test counts, E2E prerequisites, and the coverage map, see [docs/testing.md](docs/testing.md).

## Documentation

- [Architecture](docs/architecture.md): components, packages, data flow, module pipelines.
- [API reference](docs/api.md): every endpoint, request shapes, errors, SSE.
- [Security](docs/security.md): boundaries, approvals, AI trust model, residual risks.
- [Demo guide](docs/demo.md): a guided tour using only synthetic data.
- [Testing](docs/testing.md): commands, coverage, manual checklist, known gaps.

## Project layout

```text
backend/
  src/main/java/ai/ghostshift/
    ai/         Ollama client, prompt construction, validated AI results
    api/        Micronaut controllers, token filter, error contract
    config/     Immutable paths and hard limits
    db/         JDBC helpers and migration runner
    domain/     Serdeable records, enums, status constants
    service/    Module logic, safety, execution, reports, demo data
    util/       JSON, text, filesystem boundary helpers
    workflow/   Closed action registry, validator, run context
  src/main/resources/db/migration/   Versioned SQL
  src/test/java/                     JUnit 5 tests
frontend/
  src/          Vue views, components, stores, API client, design system
  e2e/          Playwright specs
docs/           Architecture, API, security, demo, testing
scripts/        Local dev and verification helpers
docker-compose.yml
```

## Honest limitations

- **No browser or screen recording.** GhostCapture records a reviewed sequence of allowlisted file actions. The API rejects `mode: "BROWSER"` rather than pretending to capture a screen.
- **No file deletion.** Duplicate detection reports exact matches; it does not remove them.
- **No arbitrary shell execution.** There is no shell action in the registry.
- **SSE exists; the UI polls.** `GET /api/v1/runs/{id}/events/stream` is implemented and tested. The Vue app uses polling so an optional token never has to travel in a URL.
- **Single-user local design.** SQLite and the in-process worker are chosen for one person on one machine. This is not a multi-tenant server.
- **Docker runtime unverified.** Configuration parses; images were not executed here.
- **AI quality varies by model.** Deterministic results remain available and are labelled separately from model interpretation. Malformed model output is rejected, not repaired into a claim.
- **Not a backup tool.** Undo covers recorded moves while the file fingerprint still matches. It is not a general file-versioning system.
