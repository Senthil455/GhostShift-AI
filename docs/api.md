# API reference

Base path: `/api/v1`. Default origin: `http://127.0.0.1:8080`.

The API is a local single-user interface used by the Vue app. It is not a public multi-tenant API. JSON is the default representation; report export is Markdown.

## Authentication

Authentication is disabled by default. If `GHOSTSHIFT_API_TOKEN` is set, every `/api/v1` route except `/api/v1/health` requires one of:

```http
Authorization: Bearer <token>
```

```http
X-GhostShift-Token: <token>
```

CORS `OPTIONS` requests are also exempt so the local dev UI can preflight. A missing or wrong token returns `401 UNAUTHORIZED`.

The browser keeps a pasted token in memory only. It is never placed in a URL or persisted.

## Error contract

Every API error returns the same shape and an `X-Request-Id` header:

```json
{
  "code": "CONFLICT",
  "message": "A file already exists at the destination. Nothing was overwritten.",
  "details": {},
  "requestId": "3f2a91c7b104",
  "timestamp": "2026-10-25T09:15:00Z"
}
```

| Status | Typical code | Meaning |
| --- | --- | --- |
| 400 | `BAD_REQUEST`, `VALIDATION_FAILED`, `INVALID_REQUEST` | Malformed input or failed validation. |
| 401 | `UNAUTHORIZED` | Token required or incorrect. |
| 403 | `FORBIDDEN` | Boundary or permission refusal (system path, symlink, read-only folder). |
| 404 | `NOT_FOUND` | Resource does not exist. |
| 409 | `CONFLICT` | Invalid state transition, stale plan, active run, destination collision. |
| 422 | `UNPROCESSABLE` | Well-formed but unusable input, such as a model draft that cannot be validated. |
| 500 | `INTERNAL_ERROR` | Unexpected local failure. Check the local log with the request id. |
| 503 | `SERVICE_UNAVAILABLE` | The local model runtime is unavailable. Nothing was saved. |

Stack traces are logged locally and never returned. Empty arrays and nullable fields are preserved in responses (`NON_NULL` serialization), so clients can rely on stable shapes.

## System

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/health` | Database and AI status. Never requires a token. |
| `GET` | `/system/info` | Version, data directory, database path, limits, token flag. |

```bash
curl http://127.0.0.1:8080/api/v1/health
```

```json
{
  "status": "ok",
  "version": "1.0.0",
  "database": "OK",
  "aiStatus": "READY",
  "aiModel": "gemma2:2b",
  "uptimeSeconds": 42,
  "timestamp": "2026-10-25T09:15:00Z"
}
```

`status` is `ok` when SQLite is reachable, `degraded` otherwise. AI status is informational; a missing model does not make the app unhealthy.

## Directories

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/directories` | List active registered folders. |
| `POST` | `/directories` | Register a folder. |
| `GET` | `/directories/{id}` | Get one folder. |
| `POST` | `/directories/{id}/scan` | Bounded read-only scan. |
| `DELETE` | `/directories/{id}` | Remove the registration. Files are untouched. |

Register:

```json
{
  "path": "C:\\Users\\me\\Downloads\\inbox",
  "displayName": "Inbox",
  "directoryType": "GENERAL",
  "allowOrganize": false
}
```

`allowOrganize` defaults to `true`. A writable folder is required when organizing. `directoryType` is normalized to one of `DOWNLOADS`, `PROJECT`, `DOCUMENTS`, or `GENERAL`; unknown values become `GENERAL`.

Scan body (optional):

```json
{ "recursive": false }
```

Scan results include `fileCount`, `truncated`, `skippedSymlinks`, and file metadata. Limits: depth 3 and 1,000 files by default. Symlinks and dependency directories are skipped.

## GhostSort: organization plans

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/organization/plans?limit=25` | Recent plan summaries. |
| `POST` | `/organization/plans` | Scan, classify, and create a review plan. Moves nothing. |
| `GET` | `/organization/plans/{planId}` | Full plan with per-file actions. |
| `PATCH` | `/organization/plans/{planId}/actions/{actionId}` | Change category or action status. |
| `POST` | `/organization/plans/{planId}/decisions` | Record approve/reject decisions. |
| `POST` | `/organization/plans/{planId}/execute` | Execute approved actions asynchronously. |
| `POST` | `/organization/plans/{planId}/cancel` | Cancel before or during execution. |
| `POST` | `/organization/plans/{planId}/undo` | Restore moved files when fingerprints still match. |

Create:

```json
{
  "directoryId": 1,
  "useAi": true,
  "recursive": false,
  "maxFiles": 200
}
```

`useAi` defaults to `true`, but deterministic rules run first. `maxFiles` is optional and clamped by server limits.

Decisions:

```json
{
  "decisions": [
    { "actionId": "uuid", "decision": "APPROVE", "category": "DOCUMENTS", "remember": true },
    { "actionId": "uuid-2", "decision": "REJECT" }
  ],
  "rejectUnreviewed": true
}
```

`decision` accepts `APPROVE`/`APPROVED` or `REJECT`/`REJECTED`. If `decisions` is omitted, all actions are approved (subject to the approval policy). `rejectUnreviewed` rejects any remaining `PROPOSED` actions. Execution requires at least one approved action and a plan in `APPROVED` state, unless the file-move approval policy is disabled.

Plan action statuses: `PROPOSED`, `APPROVED`, `REJECTED`, `MOVED`, `SKIPPED`, `FAILED`, `UNDONE`.
Plan statuses: `DRAFT`, `READY_FOR_REVIEW`, `APPROVED`, `EXECUTING`, `COMPLETED`, `PARTIALLY_COMPLETED`, `FAILED`, `CANCELLED`, `UNDONE`.

`PATCH` action body:

```json
{ "category": "IMAGES", "status": "APPROVED", "remember": false }
```

## GhostDev: analysis and reports

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/developer/analyze` | Analyze one text/log file. Read-only. |
| `GET` | `/reports?limit=50` | Report summaries. |
| `GET` | `/reports/{id}` | Full report. |
| `GET` | `/reports/{id}/export` | Markdown download. |

Analyze:

```json
{
  "directoryId": 1,
  "relativePath": "sample-app.log",
  "useAi": true
}
```

Allowed text extensions include `.log`, `.txt`, `.out`, `.err`, `.trace`, `.json`, `.csv`, `.tsv`, `.md`, `.yaml`, `.yml`, `.conf`, and `.properties`. Binary files are rejected. Deterministic grouping always runs. If `useAi` is true and Ollama is unavailable, the report is still saved with a notice; no AI text is invented.

Report response includes `title`, `contentMarkdown`, `aiUsed`, `model`, `createdAt`, and structured analysis metadata. Export sets `Content-Type: text/markdown` and a `Content-Disposition` attachment filename.

## GhostCapture: recordings

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/recordings` | List recordings with steps. |
| `POST` | `/recordings` | Start a recording. |
| `GET` | `/recordings/{id}` | Get one recording. |
| `POST` | `/recordings/{id}/pause` | Pause an active recording. |
| `POST` | `/recordings/{id}/resume` | Resume a paused recording. |
| `POST` | `/recordings/{id}/stop` | Stop a recording. |
| `DELETE` | `/recordings/{id}` | Delete a recording. |
| `POST` | `/recordings/{id}/steps` | Add an allowlisted step. |
| `PATCH` | `/recordings/{id}/steps/{stepId}` | Update description, order, or parameters. |
| `DELETE` | `/recordings/{id}/steps/{stepId}` | Delete a step. |
| `POST` | `/recordings/{id}/save` | Validate and save as a draft workflow. |

Start:

```json
{ "name": "Weekly log triage", "mode": "FILE", "directoryId": 1 }
```

Only `FILE` mode exists. `BROWSER` and screen capture return `422` with an explicit message. Add a step:

```json
{
  "actionType": "LIST_FILES",
  "description": "List the inbox",
  "parameters": { "recursive": false, "maxFiles": 100 }
}
```

Save:

```json
{ "name": "Weekly log triage", "description": "Recorded file actions.", "category": "LOG_ANALYSIS" }
```

The save endpoint re-validates the generated workflow. Invalid sequences return `422` and nothing is saved. A saved recording becomes `SAVED` and links to the draft workflow.

Recording statuses: `ACTIVE`, `PAUSED`, `STOPPED`, `SAVED`.

## GhostReplay: workflows and runs

### Workflows

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/workflows?includeArchived=false` | List workflow summaries. |
| `POST` | `/workflows` | Create a workflow. |
| `GET` | `/workflows/{id}` | Get definition, validation, permissions, risks. |
| `PUT` | `/workflows/{id}` | Update. Editing an approved definition resets it to review. |
| `DELETE` | `/workflows/{id}` | Archive. |
| `POST` | `/workflows/{id}/restore` | Restore from archive. |
| `PATCH` | `/workflows/{id}/status` | Set status. |
| `POST` | `/validation` | Validate a definition without saving. |
| `POST` | `/draft` | Ask the local model for a draft. Nothing is saved. |
| `GET` | `/actions` | Closed action catalogue with parameter specs. |
| `POST` | `/workflows/{id}/runs` | Start a dry or live run. |

Create/update body:

```json
{
  "name": "Tidy a folder",
  "description": "Classify and move files after review.",
  "category": "FILE_ORGANIZATION",
  "definition": {
    "schemaVersion": 1,
    "name": "Tidy a folder",
    "description": "Classify and move files after review.",
    "requiresApproval": true,
    "failurePolicy": "STOP",
    "inputs": [
      {
        "name": "directoryId",
        "label": "Folder",
        "type": "DIRECTORY",
        "required": true,
        "description": "The registered folder to organize."
      }
    ],
    "steps": [
      { "id": "list", "type": "LIST_FILES", "description": "List files.",
        "parameters": { "recursive": false, "maxFiles": 100 }, "enabled": true },
      { "id": "classify", "type": "CLASSIFY_FILES", "description": "Classify by filename.",
        "parameters": { "useAi": false }, "enabled": true },
      { "id": "propose", "type": "PROPOSE_DESTINATION", "description": "Compute safe destinations.",
        "parameters": {}, "enabled": true },
      { "id": "preview", "type": "PREVIEW_FILE_MOVE", "description": "Preview moves.",
        "parameters": {}, "enabled": true },
      { "id": "approve", "type": "REQUEST_USER_APPROVAL", "description": "Pause for a human.",
        "parameters": { "title": "Approve moves", "explanation": "Review the preview first." }, "enabled": true },
      { "id": "move", "type": "MOVE_FILE", "description": "Move approved files.",
        "parameters": { "maxFiles": 100 }, "enabled": true }
    ]
  }
}
```

`failurePolicy` is `STOP` or `CONTINUE_SAFE`. Up to 40 steps. Step ids use letters, numbers, dot, dash, or underscore. The validator enforces ordering: `MOVE_FILE` needs `LIST_FILES`, `PROPOSE_DESTINATION`, and `PREVIEW_FILE_MOVE`; `ANALYZE_LOG` needs `READ_APPROVED_TEXT`; workflows that read or move need a `directoryId` input of type `DIRECTORY`.

Statuses: `DRAFT`, `READY_FOR_REVIEW`, `APPROVED`, `DISABLED`, `ARCHIVED`. A live run requires `APPROVED`. A dry run is allowed while under review as long as validation passes.

Draft:

```json
{ "prompt": "Find log files, analyze the newest one, and save a report.", "directoryId": 1 }
```

Returns the proposed definition, a notice, validation errors/warnings, and `aiUsed`. A model failure returns `503` and saves nothing. Invalid model JSON returns `422` and saves nothing.

Run:

```json
{ "inputs": { "directoryId": 1 }, "dryRun": true }
```

Dry runs do not move files, write reports, or request approvals; approval steps are recorded as skipped. A live run is queued on the local worker and returns immediately.

### Runs

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/runs?limit=25` | Recent run summaries. |
| `GET` | `/runs/{runId}` | Run, action executions, outputs, and approvals. |
| `GET` | `/runs/{runId}/events?after=0` | Durable events after an id. |
| `GET` | `/runs/{runId}/events/stream?after=0` | SSE stream of durable events. |
| `POST` | `/runs/{runId}/cancel` | Request cancellation. |

Run statuses: `QUEUED`, `RUNNING`, `WAITING_FOR_APPROVAL`, `COMPLETED`, `PARTIALLY_COMPLETED`, `FAILED`, `CANCELLED`.
Step statuses: `PENDING`, `RUNNING`, `WAITING_FOR_APPROVAL`, `SUCCEEDED`, `FAILED`, `SKIPPED`, `CANCELLED`.

Polling example:

```bash
curl "http://127.0.0.1:8080/api/v1/runs/<runId>/events?after=12"
```

SSE:

```bash
curl -N "http://127.0.0.1:8080/api/v1/runs/<runId>/events/stream?after=0"
```

```text
id: 13
data: {"id":13,"runId":"...","at":"2026-10-25T09:15:00Z","level":"INFO","message":"Workflow run started.","data":{}}

id: 14
data: {"id":14,"runId":"...","at":"2026-10-25T09:15:01Z","level":"INFO","message":"Step succeeded: list","data":{"actionType":"LIST_FILES","stepId":"list"}}
```

The stream replays persisted events after `after`, polls the database every 500 ms, and completes once the run reaches a terminal status. It is implemented and integration-tested. The Vue UI intentionally uses polling so a token never has to be put in an event-source URL.

## Approvals

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/approvals` | Pending approvals. |
| `POST` | `/approvals/{id}/approve` | Approve and resume the run. |
| `POST` | `/approvals/{id}/reject` | Reject and fail the run safely. |

Decision body is optional:

```json
{ "note": "Reviewed the preview. Proceed with these three files." }
```

Approval response is the updated run. Approving a workflow move gate does not grant a global approval; each gate is tied to a run and action.

## GhostPulse: insights and activity

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/insights/dashboard` | Counts, AI state, recent activity, pending approvals. |
| `GET` | `/insights/time-savings` | Estimated savings with assumptions. |
| `GET` | `/insights/duplicates?directoryId=1&recursive=false` | Exact duplicate groups by SHA-256. |
| `GET` | `/activity?page=1&pageSize=25&kind=` | Paginated activity history. |

Duplicate results contain exact hash groups only. Similar filenames are not treated as proof. The endpoint is read-only and never deletes duplicates.

Time savings is explicitly an estimate derived from configured minutes per file, log analysis, and workflow review. The assumptions are returned so the UI can show them rather than presenting a fabricated measured result.

## Settings and GhostBrain

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/settings` | Model, approval policy, time assumptions, theme, onboarding, folders. |
| `PATCH` | `/settings` | Update any subset. |
| `POST` | `/settings/model/test` | Test an endpoint/model without saving. |
| `GET` | `/ai/status?refresh=false` | Ollama reachability and installed models. |

Patch examples:

```json
{ "theme": "light", "onboardingCompleted": true }
```

```json
{
  "model": {
    "provider": "ollama",
    "endpoint": "http://localhost:11434",
    "model": "gemma2:2b",
    "timeoutSeconds": 120,
    "maxInputChars": 12000
  }
}
```

```json
{
  "approvals": {
    "fileMovesRequireApproval": true,
    "workflowMovesRequireApproval": true,
    "logAnalysisRequiresApproval": false
  }
}
```

```json
{
  "categoryFolders": {
    "DOCUMENTS": "Documents",
    "IMAGES": "Images",
    "LOGS": "Logs"
  }
}
```

Theme accepts only `dark` or `light` from the API; the client resolves `system` locally. Folder names are sanitized by the backend. The provider must be `ollama`. Timeout is 5-600 seconds; max input is 500-200,000 characters.

Model test body (optional overrides):

```json
{ "endpoint": "http://localhost:11434", "model": "gemma2:2b" }
```

The test performs a tiny real inference. It does not claim success from a port check alone.

`/ai/status` returns `status` values such as `READY`, `MODEL_MISSING`, `UNAVAILABLE`, with a message and installed model list. `refresh=true` bypasses the 20-second cache.

## Data

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/demo/seed` | Create or reuse synthetic demo files and example workflows. |
| `GET` | `/data/export` | Export GhostShift metadata as JSON. |
| `POST` | `/data/reset` | Clear selected local data after confirmation. |

Seed returns the demo directory id, files created, workflows created, and a message. Seeding is idempotent and never overwrites demo files.

Reset:

```json
{ "scope": "ALL", "confirmation": "DELETE" }
```

Scopes: `ACTIVITY`, `RUNS`, `REPORTS`, `PLANS`, `ALL`. `ALL` also clears workflows, recordings, preferences, classification memory, and the onboarding/theme state. Registered folders remain. The reset refuses to clear runs while a run is active or plans while one is executing. **No user file is deleted, moved, or modified.**

Export contains folders, workflows, plans, runs, reports, approvals, activity, and settings, with a notice that it does not contain user file contents. Generated report Markdown is stored separately.

## Action catalogue

`GET /workflows/actions` returns the closed registry. The same list is enforced by the backend validator.

| Type | Risk | Permission | Approval | Purpose |
| --- | --- | --- | --- | --- |
| `LIST_FILES` | low | `files:read` | no | List files inside the registered folder. |
| `FILTER_FILES` | low | `files:read` | no | Filter by extension and/or name fragment. |
| `CLASSIFY_FILES` | low | `files:read` | no | Deterministic classification, optional local model. |
| `PROPOSE_DESTINATION` | low | `files:read` | no | Compute app-owned destinations. |
| `PREVIEW_FILE_MOVE` | low | `files:read` | no | Check boundaries and conflicts. |
| `MOVE_FILE` | moderate | `files:write` | yes | Move previewed files without overwriting. |
| `HASH_FILE` | low | `files:read` | no | SHA-256 and exact duplicate grouping. |
| `READ_APPROVED_TEXT` | low | `files:read` | no | Bounded text read inside the folder. |
| `ANALYZE_LOG` | low | `ai:local` | policy | Deterministic log analysis plus optional model interpretation. |
| `GENERATE_REPORT` | low | `reports:write` | no | Save a Markdown report in app storage. |
| `REQUEST_USER_APPROVAL` | low | `workflow:approve` | no | Pause for an explicit human decision. |

There is no shell, HTTP request, delete, rename, copy, or arbitrary path action. Unknown action types are rejected during validation.

## Concurrency and lifecycle notes

- Long-running plans and workflow runs execute on a two-thread local worker. Requests return immediately.
- Plans and runs use explicit state transitions. Executing a plan twice, running an unapproved workflow live, or clearing active runs returns `409`.
- On startup, runs left `QUEUED` or `RUNNING` by a previous process are marked failed rather than silently resumed.
- SQLite uses WAL mode with a busy timeout. The app is designed for one local user, not concurrent remote clients.
