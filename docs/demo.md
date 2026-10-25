# Demo guide

This walkthrough uses only synthetic data created by GhostShift. It does not require access to personal files and does not require Ollama for the deterministic path.

## 1. Start clean

```powershell
.\scripts\dev.ps1
```

Open `http://127.0.0.1:5173`. On first run the Welcome screen appears. Choose **Create demo workspace** to create the synthetic folder and example workflows, or choose **Enter the workspace** and use **Settings -> Local data -> Create demo** later.

The demo creates:

```text
<data-dir>/demo/inbox/
  README.txt
  invoice-2026-03.txt
  meeting-notes.txt
  sample-app.log
  duplicates/
    meeting-notes-copy.txt
```

It registers `<data-dir>/demo/inbox` as **GhostShift demo inbox**. On Windows with the default data directory, that is under `backend/data` or the configured `GHOSTSHIFT_DATA_DIR`.

Seeded workflows:

- **Tidy a folder (example)**: list, classify without AI, propose, preview, human approval, move.
- **Triage a log file (example)**: list, filter `.log`, bounded read, deterministic analysis, report.
- **Find duplicate files (example)**: recursive list and SHA-256 duplicate grouping. Read-only.

Seeding is idempotent: it never overwrites a demo file or duplicates a workflow. It does not fabricate run history.

## 2. GhostSort: preview before any move

1. Open **GhostSort**.
2. Select **GhostShift demo inbox**.
3. Leave **Ask GhostBrain about ambiguous files** off for the first pass. Click **Build plan**.
4. Open the new plan. Every file has a proposed category, destination, confidence, and source (`RULE`, `MEMORY`, or `AI`).
5. Reject one file, change a category on another, and approve the rest. Use **Approve proposed** only after reading the list.
6. Click **Execute approved moves**. Files move into `GhostShift Organizer/<Category>/` inside the demo folder.
7. Open the completed plan and click **Undo** to verify the fingerprint-guarded restore.

What to notice:

- Nothing moves during scanning or planning.
- The destination is always inside the registered folder.
- A name collision gets a non-colliding name such as `invoice (1).txt`; nothing is overwritten.
- If a file changed after the plan was created, execution refuses that file and reports it.
- Undo refuses if the moved file changed after the move.

Re-running the demo after a move will not find the original files at the old paths. Use a fresh data directory, move them back yourself, or create another demo folder with a new data directory if you want a pristine tour.

## 3. GhostDev: evidence before interpretation

1. Open **GhostDev**.
2. Select **GhostShift demo inbox**, scan, and choose `sample-app.log`.
3. Analyze with AI off. The report shows line counts, grouped error signatures, counts, and line numbers. This is deterministic and works without Ollama.
4. Turn AI on and analyze again. If Ollama and the selected model are available, the report gains a clearly labelled interpretation with possible causes and recommended checks. If not, the report still saves with an honest notice.
5. Open **History -> Reports** to inspect or export the Markdown.

The demo log contains repeated connection-refused errors and a timeout. A good result distinguishes observed evidence ("three connection-refused signatures") from a hypothesis ("the database may not be running"). It should not claim a confirmed root cause.

## 4. GhostCapture: record file actions, not a screen

GhostCapture records an allowlisted sequence of file actions. It does **not** capture a browser or your screen; that mode is rejected by the API.

1. Open **GhostCapture**.
2. Start a recording named `Demo log triage`, attach **GhostShift demo inbox** as the working folder, and click **Start recording**.
3. Add steps from the catalogue in a valid order:
   - `LIST_FILES` with `recursive: false`, `maxFiles: 100`
   - `FILTER_FILES` with extensions `[".log"]`
   - `READ_APPROVED_TEXT` with `maxChars: 200000`
   - `ANALYZE_LOG` with `useAi: false`
   - `GENERATE_REPORT` with title `Demo log triage`
4. Pause, resume, edit a description, and reorder a step to see the editor.
5. Stop the recording, then **Save as workflow**. It becomes a draft or reviewable workflow, depending on validation.

The saved workflow is not executed automatically. Open **GhostReplay** to dry-run it.

## 5. GhostReplay: dry run, approval, live run

1. Open **GhostReplay**.
2. Select **Tidy a folder (example)**.
3. Choose the demo inbox and click **Start dry run**. The run record opens. Dry runs do not move files, write reports, or request approvals.
4. Return to GhostReplay and start the same workflow live. The workflow status must be **Approved**; seeded examples already are.
5. The run pauses at **Approve the previewed file moves**. Open **Approvals**, read the explanation and note, then approve or reject.
6. Approval resumes the run. **History -> Runs** shows every step, its status, outputs, and the approval decision.

A rejection fails the run safely. Cancelling a waiting run expires the approval and marks remaining steps cancelled. There is no way to approve a move gate from a different run, and a log-analysis approval does not authorize a move.

## 6. GhostBrain: a real local model or an honest failure

1. Open **GhostBrain**.
2. The status card shows `READY`, `MODEL_MISSING`, or `UNAVAILABLE`. `MODEL_MISSING` is not a success; it tells you to pull the model.
3. If needed, start Ollama and pull the model:

   ```bash
   ollama serve
   ollama pull gemma2:2b
   ```

4. Click **Test model**. This performs a tiny real inference and reports latency.
5. Change the endpoint to an unreachable URL and test again. The UI shows a clear failure. Restore the correct endpoint and save.
6. Use **Draft a workflow** with a prompt such as: "List log files in the chosen folder, analyze the newest one, and save a report." The result is a proposal with validation errors and warnings. Nothing is saved or executed automatically. Open it in the editor, fix anything, and save explicitly.

The model sees filenames, extensions, sizes, and bounded log signatures, not arbitrary file contents. It cannot choose a destination path or add an action.

## 7. GhostPulse: estimates and exact evidence

1. Open **GhostPulse**.
2. Review the time-savings card. The assumptions (minutes per file, per log, per workflow review) are shown and editable in **Settings**. The number is an estimate, not a measured study.
3. Under **Duplicate evidence**, select the demo inbox and scan. `meeting-notes.txt` and `duplicates/meeting-notes-copy.txt` should appear as an exact SHA-256 match.
4. Similar names without identical hashes are not presented as duplicates.
5. The local system panel shows the data directory, database path, limits, and API token state.

GhostPulse is read-only. It does not delete duplicate files.

## 8. History, approvals, and settings

- **Approvals** lists pending gates and the policy that created them. Decisions can include a note.
- **History** has tabs for runs, activity, and reports. Filters are local; no data leaves the browser.
- **Settings** covers the model, registered folders, approval policy, time assumptions, category folder names, data export/seed/reset, appearance, and the optional access token.
- **Welcome** can be replayed from Settings without clearing data.

## 9. Reset the demo state

**Settings -> Local data -> Clear local data** requires typing `DELETE`. Scope `ALL` clears workflows, recordings, preferences, activity, runs, plans, reports, and classification memory. It does **not** delete the demo files or the folder registration. To remove the registration, use **Settings -> Registered folders -> Remove**.

If you want a truly fresh demo, stop the backend and start it with a new data directory:

```powershell
$env:GHOSTSHIFT_DATA_DIR = "$env:TEMP\ghostshift-demo-fresh"
.\scripts\dev.ps1 -DataDir $env:GHOSTSHIFT_DATA_DIR
```

## What is intentionally not in the demo

- Browser or screen recording.
- File deletion or automatic duplicate cleanup.
- Arbitrary shell commands.
- A cloud model or account.
- Fabricated run history or fabricated AI output.

If Ollama is offline, the demo still demonstrates scanning, classification, planning, approvals, guarded moves, undo, deterministic log analysis, duplicate hashing, recording, dry runs, history, and reset. AI-specific panels explain what is missing instead of inventing a result.
