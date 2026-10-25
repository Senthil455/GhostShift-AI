# Security

GhostShift AI is designed for one person running it on one machine, against folders that person explicitly registers. This document states what is enforced, where the enforcement lives, and which risks remain.

## Design rule

```text
AI suggests -> deterministic code validates -> user authorizes -> engine executes -> history records
```

The model is never an authority. It cannot name a destination path, add a workflow action, grant an approval, run a command, or change a policy.

## Threat model

In scope:

- A local model returns malformed JSON, hallucinated categories, or text that tries to act like an instruction.
- A filename or log line contains prompt-injection text.
- A file is moved or changed between preview and execution.
- A path attempts `..` traversal, an absolute destination, a symlink escape, or a system-directory target.
- A destination already exists and would be overwritten.
- A local process on the same machine probes the HTTP API.
- A user pastes a token into the UI and expects it not to be persisted.

Out of scope for v1:

- A hostile user with the same OS account as the GhostShift process. Such a user can read the SQLite file and modify the app.
- A compromised operating system, JDK, browser, or Ollama installation.
- Multi-tenant isolation. The API is not designed to be exposed to untrusted networks.
- Malware analysis. GhostDev parses text; it does not detonate or sandbox untrusted binaries.
- Encryption at rest. The database and reports are plain local files.

## Filesystem boundary

All user-file access passes through `PathGuard` and, for mutations, `SafeFileOperations`.

| Control | Implementation |
| --- | --- |
| Registration resolves the real path | `PathGuard.validateRegistration` uses `toRealPath()` and requires a readable directory. |
| System directories refused | Windows: `C:\Windows`, `Program Files`, `ProgramData`, `$Recycle.Bin`, and related roots. Unix: `/`, `/etc`, `/usr`, `/bin`, `/sbin`, `/lib`, `/boot`, `/proc`, `/sys`, `/dev`, `/var`. The user's home directory itself is also refused. |
| Reads stay inside the root | `resolveExistingFile` normalizes, checks `startsWith(root)`, rejects symlink components, resolves the real path, and re-checks containment. |
| Writes stay inside the root | `resolveForWrite` requires a relative path, normalizes, checks containment, rejects symlink components, and refuses a symlink target. |
| Symlinks are not followed | Scanner skips symlinked files and directories. Path resolution rejects symlink components. Moves and undo refuse symlinks. |
| Scan is bounded | Maximum depth and file count are clamped by `AppConfig`; defaults are depth 3 and 1,000 files. `.git`, `node_modules`, `build`, `dist`, virtual environments, and the app's own organizer folder are skipped. |
| Text reads are bounded | `max-log-bytes` defaults to 2 MiB; workflow `READ_APPROVED_TEXT` has an explicit character cap; AI input is capped by `max-input-chars` (default 12,000). |
| No deletes | There is no delete action for user files. The API can archive a workflow, delete GhostShift-owned recordings/reports/plans, and clear its own database rows, never a registered file. |
| No overwrites | `SafeFileOperations.moveVerified` checks destination existence, uses a non-colliding name, and retries a `FileAlreadyExistsException` with a new name. |
| Change detection | A move verifies the source's recorded size and last-modified timestamp, then records SHA-256 for undo. |
| Undo is conditional | Undo verifies the moved file's size and hash, refuses an occupied original path, and never overwrites. |
| No arbitrary shell | The action registry has no shell, script, or HTTP-request action. |

There is an inherent check-then-act window between verification and `Files.move`. The code closes the common case by re-checking the destination and using a no-replace move, but no portable Java API offers a single atomic "rename if absent and fingerprint matches" primitive on every filesystem. If the source changes in that tiny window, the recorded fingerprint may not match the moved bytes. This is a residual risk, not a claim of transactional filesystem semantics.

## Approvals

Approvals are durable rows tied to a run and action. They are enforced by the backend executor.

| Gate | Default | Behavior |
| --- | --- | --- |
| GhostSort plan moves | On | A plan must be reviewed and explicit decisions recorded before execution. With the policy off, `execute` can approve remaining actions programmatically; the UI still shows the plan first. |
| Workflow `MOVE_FILE` | On | The executor pauses before the move unless the run context already has an explicit approval. A prior log-analysis approval does not count. |
| `REQUEST_USER_APPROVAL` | Always | Pauses a live run until a human approves or rejects. Dry runs skip the pause and mark it as skipped. |
| Log analysis (`ANALYZE_LOG`) | Off | Can be enabled in Settings. It creates a separate gate. |

If a user disables an approval policy, they have chosen to reduce a safety layer. The UI states this; the backend honors the configured policy rather than silently overriding it. The action registry still requires approval metadata for `MOVE_FILE`, and the plan execution path still verifies boundaries.

Approval decisions accept an optional note. Pending approvals can be approved or rejected from the Approvals view or the API. A rejected gate fails the run safely; it does not silently continue.

## AI trust boundary

- **No tool use.** The model receives text and returns text. It cannot call an action or the filesystem.
- **Closed schemas.** Classification results must use an allowed category key. Workflow drafts are parsed into the action registry and validated. Log insights are parsed into bounded fields.
- **No paths from the model.** Destinations come from `Category` folder names configured in the app and sanitized by `SettingsService`. A model-supplied path is ignored.
- **Prompt-injection handling.** System prompts state that filenames and log content are untrusted data, delimit them in tags, and forbid following embedded instructions. Output is still treated as untrusted and validated.
- **Data minimization.** Filename classification sends names, extensions, and sizes only. File contents are not sent. Log analysis sends grouped error signatures, not the whole raw file, bounded by `max-input-chars`.
- **Honest failure.** If Ollama is unreachable or returns invalid output, AI-assisted features return a clear notice. Deterministic evidence remains available. GhostShift does not synthesize a fake model answer.
- **Bounded output.** Token limits, timeouts, and response length checks apply. Redirects from the Ollama endpoint are disabled.
- **Labelling.** Reports and UI distinguish deterministic evidence from model interpretation.

The endpoint is user-configurable and may point at any HTTP(S) host. That is deliberate for local runtimes, but it means an attacker who can change settings can make the backend send requests to an internal address. The API is loopback-bound by default and protected by an optional token, which limits this. Do not expose the backend to an untrusted network.

## HTTP API

| Control | Detail |
| --- | --- |
| Default bind | `127.0.0.1:8080`; Docker publishes to `127.0.0.1` only. |
| Optional token | `GHOSTSHIFT_API_TOKEN`. Every `/api/v1` route except `/api/v1/health` and CORS `OPTIONS` requires `Authorization: Bearer <token>` or `X-GhostShift-Token`. |
| Comparison | `MessageDigest.isEqual` on UTF-8 bytes, avoiding a short-circuiting string compare. |
| Token storage | The server reads the token from the environment. It is not written to SQLite or logs. The browser keeps a pasted token in a module variable only; refresh clears it. |
| CORS | Only the local Vite/preview origins are allowed, with the methods and headers the app uses. |
| Request size | Micronaut `max-request-size` is 2 MB; nginx also caps at 2 MB. |
| Errors | One JSON contract (`code`, `message`, `details`, `requestId`, `timestamp`) with `X-Request-Id`. Stack traces are logged locally, never returned. |
| Redirects | The Ollama HTTP client does not follow redirects. |
| Same-origin UI | The Vite proxy and nginx proxy keep `/api` same-origin, so no wildcard CORS is needed in production packaging. |

The token is a local convenience, not a substitute for OS access control. Anyone who can read the environment or the process memory can read it.

## Data at rest

- `<data-dir>/ghostshift.db` contains folders, workflows, runs, approvals, activity, settings, and report metadata.
- `<data-dir>/reports/*.md` contains generated Markdown reports.
- `<data-dir>/demo/` contains synthetic demo files.
- API tokens are never stored. Model endpoint/model settings are stored because they are configuration, not secrets.
- File names, paths, and log signatures can appear in the database, reports, and local logs. Treat the data directory as sensitive.
- **No encryption at rest.** Use OS full-disk encryption if that matters for your threat model.

### Local data reset

`POST /api/v1/data/reset` requires `confirmation: "DELETE"` and a scope of `ACTIVITY`, `RUNS`, `REPORTS`, `PLANS`, or `ALL`. It refuses to clear runs while a run is active and refuses to clear plans while a plan is executing. It deletes only GhostShift's database rows and generated reports. It does not delete, move, or modify registered user files. Registered folders are retained; `ALL` clears workflows, recordings, preferences, activity, runs, plans, reports, and classification memory.

## Deployment guidance

- Keep the default loopback bind. If you must expose the API, put it behind a reverse proxy with TLS and authentication, and set `GHOSTSHIFT_API_TOKEN`.
- Do not run GhostShift as an administrator or root.
- Register the narrowest folder that solves the task. Prefer a read-only registration when you only need analysis.
- Review the action catalogue and approval policy before running a workflow you did not write.
- Keep Ollama local. A remote endpoint receives the text GhostShift sends, which may include filenames or log signatures.
- Back up the data directory if you care about history. SQLite WAL files should be included in a consistent backup.

## Reporting a security issue

This is a local project without a hosted service or bug-bounty program. If you find a vulnerability, open a private report to the repository owner with reproduction steps and the affected version. Do not include real personal file contents or API tokens in a report.
