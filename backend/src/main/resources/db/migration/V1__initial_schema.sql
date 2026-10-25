-- GhostShift AI initial schema.
-- All timestamps are ISO-8601 UTC strings for portable comparisons.

CREATE TABLE registered_directory (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    display_name TEXT NOT NULL,
    canonical_path TEXT NOT NULL UNIQUE,
    directory_type TEXT NOT NULL DEFAULT 'GENERAL',
    allow_read INTEGER NOT NULL DEFAULT 1,
    allow_organize INTEGER NOT NULL DEFAULT 1,
    active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    last_scan_at TEXT
);

CREATE TABLE organization_plan (
    id TEXT PRIMARY KEY,
    directory_id INTEGER NOT NULL REFERENCES registered_directory(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    file_count INTEGER NOT NULL DEFAULT 0,
    conflict_count INTEGER NOT NULL DEFAULT 0,
    summary TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    executed_at TEXT
);

CREATE TABLE organization_action (
    id TEXT PRIMARY KEY,
    plan_id TEXT NOT NULL REFERENCES organization_plan(id) ON DELETE CASCADE,
    source_relative TEXT NOT NULL,
    destination_relative TEXT,
    file_name TEXT NOT NULL,
    extension TEXT,
    size_bytes INTEGER NOT NULL DEFAULT 0,
    modified_at TEXT,
    category TEXT NOT NULL,
    confidence REAL NOT NULL DEFAULT 0.5,
    rationale TEXT,
    classification_source TEXT NOT NULL DEFAULT 'RULE',
    status TEXT NOT NULL DEFAULT 'PROPOSED',
    conflict INTEGER NOT NULL DEFAULT 0,
    error TEXT
);

CREATE INDEX idx_plan_action_plan ON organization_action(plan_id);
CREATE INDEX idx_plan_action_status ON organization_action(status);

CREATE TABLE report (
    id TEXT PRIMARY KEY,
    kind TEXT NOT NULL,
    title TEXT NOT NULL,
    source_relative TEXT,
    content_markdown TEXT NOT NULL,
    evidence_json TEXT,
    ai_used INTEGER NOT NULL DEFAULT 0,
    model TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE workflow (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    category TEXT NOT NULL DEFAULT 'GENERAL',
    current_version INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    archived INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE workflow_version (
    id TEXT PRIMARY KEY,
    workflow_id TEXT NOT NULL REFERENCES workflow(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    definition_json TEXT NOT NULL,
    validation_json TEXT,
    created_at TEXT NOT NULL,
    UNIQUE(workflow_id, version)
);

CREATE TABLE workflow_run (
    id TEXT PRIMARY KEY,
    workflow_id TEXT,
    workflow_name TEXT NOT NULL,
    workflow_version INTEGER NOT NULL,
    status TEXT NOT NULL,
    dry_run INTEGER NOT NULL DEFAULT 0,
    trigger_type TEXT NOT NULL DEFAULT 'MANUAL',
    inputs_json TEXT,
    context_json TEXT,
    summary TEXT,
    cancel_requested INTEGER NOT NULL DEFAULT 0,
    current_step INTEGER NOT NULL DEFAULT 0,
    started_at TEXT,
    ended_at TEXT,
    duration_ms INTEGER
);

CREATE INDEX idx_run_status ON workflow_run(status);

CREATE TABLE action_execution (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES workflow_run(id) ON DELETE CASCADE,
    step_id TEXT NOT NULL,
    step_index INTEGER NOT NULL,
    action_type TEXT NOT NULL,
    status TEXT NOT NULL,
    parameters_json TEXT,
    result_json TEXT,
    error TEXT,
    started_at TEXT,
    ended_at TEXT,
    UNIQUE(run_id, step_id)
);

CREATE INDEX idx_action_run ON action_execution(run_id);

CREATE TABLE approval_request (
    id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL REFERENCES workflow_run(id) ON DELETE CASCADE,
    action_execution_id TEXT,
    kind TEXT NOT NULL DEFAULT 'ACTION',
    title TEXT NOT NULL,
    explanation TEXT NOT NULL,
    risk TEXT NOT NULL DEFAULT 'MODERATE',
    status TEXT NOT NULL DEFAULT 'PENDING',
    resolution_note TEXT,
    created_at TEXT NOT NULL,
    resolved_at TEXT
);

CREATE INDEX idx_approval_status ON approval_request(status);

CREATE TABLE file_operation (
    id TEXT PRIMARY KEY,
    run_id TEXT,
    plan_id TEXT,
    original_path TEXT NOT NULL,
    destination_path TEXT NOT NULL,
    operation_type TEXT NOT NULL DEFAULT 'MOVE',
    status TEXT NOT NULL,
    recovery_json TEXT,
    error TEXT,
    created_at TEXT NOT NULL,
    undone_at TEXT
);

CREATE INDEX idx_fileop_plan ON file_operation(plan_id);

CREATE TABLE user_preference (
    preference_key TEXT PRIMARY KEY,
    value_json TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE model_configuration (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    provider TEXT NOT NULL DEFAULT 'ollama',
    endpoint TEXT NOT NULL,
    model TEXT NOT NULL,
    timeout_seconds INTEGER NOT NULL DEFAULT 120,
    max_input_chars INTEGER NOT NULL DEFAULT 12000,
    last_health_status TEXT,
    last_health_at TEXT,
    last_latency_ms INTEGER
);

CREATE TABLE recording (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    mode TEXT NOT NULL DEFAULT 'FILE',
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    directory_id INTEGER REFERENCES registered_directory(id) ON DELETE SET NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    stopped_at TEXT,
    workflow_id TEXT
);

CREATE TABLE recorded_step (
    id TEXT PRIMARY KEY,
    recording_id TEXT NOT NULL REFERENCES recording(id) ON DELETE CASCADE,
    step_order INTEGER NOT NULL,
    action_type TEXT NOT NULL,
    description TEXT NOT NULL,
    parameters_json TEXT NOT NULL DEFAULT '{}',
    requires_approval INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_recorded_step_recording ON recorded_step(recording_id, step_order);

CREATE TABLE classification_memory (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    kind TEXT NOT NULL,
    match_key TEXT NOT NULL,
    category TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(kind, match_key)
);

CREATE TABLE run_event (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id TEXT NOT NULL REFERENCES workflow_run(id) ON DELETE CASCADE,
    at TEXT NOT NULL,
    level TEXT NOT NULL DEFAULT 'INFO',
    message TEXT NOT NULL,
    data_json TEXT
);

CREATE INDEX idx_run_event_run ON run_event(run_id, id);

CREATE TABLE activity (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    kind TEXT NOT NULL,
    title TEXT NOT NULL,
    detail TEXT,
    entity_id TEXT,
    at TEXT NOT NULL
);

CREATE INDEX idx_activity_at ON activity(at DESC);
