<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/api/client'
import type { RecordedStepView, RecordingView, WorkflowActionView } from '@/api/types'
import { errorDetails, errorMessage } from '@/lib/errors'
import { humanDate, plural, relativeTime, titleCase } from '@/lib/format'
import { useSessionStore } from '@/stores/session'
import { useToastStore } from '@/stores/toast'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import DirectoryPicker from '@/components/DirectoryPicker.vue'
import EmptyState from '@/components/EmptyState.vue'
import Icon from '@/components/Icon.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'

const session = useSessionStore()
const toast = useToastStore()

const recordings = ref<RecordingView[]>([])
const selected = ref<RecordingView | null>(null)
const loading = ref(false)
const error = ref('')
const name = ref('')
const directoryId = ref<number | null>(null)
const starting = ref(false)

const actionType = ref('LIST_FILES')
const stepDescription = ref('')
const parameters = ref('{}')
const addingStep = ref(false)
const stepError = ref('')

const saveName = ref('')
const saveDescription = ref('')
const saveCategory = ref('CUSTOM')
const saving = ref(false)
const saveError = ref('')
const deleteOpen = ref(false)
const deleteBusy = ref(false)

const selectedDirectory = computed(
  () => session.directories.find((directory) => directory.id === selected.value?.directoryId) ?? null,
)
const selectedAction = computed(() => session.actions.find((action) => action.type === actionType.value) ?? null)
const editable = computed(() =>
  selected.value ? ['ACTIVE', 'PAUSED', 'STOPPED'].includes(selected.value.status) : false,
)
const canSave = computed(() => editable.value && (selected.value?.steps.length ?? 0) > 0)
const categoryOptions = ['CUSTOM', 'FILE_ORGANIZATION', 'LOG_ANALYSIS', 'DEVELOPER', 'GENERAL']

const EXAMPLES: Record<string, Record<string, unknown>> = {
  LIST_FILES: { recursive: false, maxFiles: 100 },
  FILTER_FILES: { extensions: ['.log'] },
  CLASSIFY_FILES: { useAi: false },
  PROPOSE_DESTINATION: {},
  PREVIEW_FILE_MOVE: {},
  MOVE_FILE: { maxFiles: 100 },
  HASH_FILE: {},
  READ_APPROVED_TEXT: { maxChars: 200000 },
  ANALYZE_LOG: { useAi: false },
  GENERATE_REPORT: { title: 'Recorded summary' },
  REQUEST_USER_APPROVAL: {
    title: 'Review the recorded actions',
    explanation: 'The recording paused here so a human can verify the next step.',
  },
}

function actionLabel(type: string): string {
  return session.actions.find((action) => action.type === type)?.label ?? titleCase(type)
}

function actionMeta(type: string): WorkflowActionView | null {
  return session.actions.find((action) => action.type === type) ?? null
}

function parameterHints(action: WorkflowActionView | null): string[] {
  if (!action) return []
  return Object.entries(action.parameters).map(([key, spec]) => {
    const type = String(spec.type ?? 'value')
    const min = spec.min
    const max = spec.max
    const range =
      typeof min === 'number' && typeof max === 'number' && max > 0 && type !== 'boolean'
        ? ` · ${min}–${max}`
        : ''
    return `${key}: ${type}${range}`
  })
}

function pretty(value: unknown): string {
  return JSON.stringify(value ?? {}, null, 2)
}

async function loadRecordings() {
  loading.value = true
  try {
    recordings.value = await api.recordings()
    if (selected.value) {
      selected.value = recordings.value.find((item) => item.id === selected.value?.id) ?? null
    }
  } catch (cause) {
    error.value = errorMessage(cause, 'Recordings could not be loaded.')
  } finally {
    loading.value = false
  }
}

async function select(recording: RecordingView) {
  error.value = ''
  try {
    selected.value = await api.recording(recording.id)
    saveName.value = selected.value.name
    saveDescription.value = ''
    saveCategory.value = 'CUSTOM'
    saveError.value = ''
  } catch (cause) {
    error.value = errorMessage(cause, 'That recording could not be opened.')
  }
}

async function start() {
  if (!name.value.trim()) {
    error.value = 'Give this recording a name.'
    return
  }
  starting.value = true
  error.value = ''
  try {
    const recording = await api.startRecording({
      name: name.value.trim(),
      mode: 'FILE',
      directoryId: directoryId.value ?? undefined,
    })
    selected.value = recording
    saveName.value = recording.name
    name.value = ''
    await loadRecordings()
    toast.success('Recording started', 'Add allowlisted file actions, then save a draft workflow.')
  } catch (cause) {
    error.value = errorMessage(cause, 'The recording could not be started.')
  } finally {
    starting.value = false
  }
}

async function control(action: 'pause' | 'resume' | 'stop') {
  if (!selected.value) return
  try {
    if (action === 'pause') selected.value = await api.pauseRecording(selected.value.id)
    if (action === 'resume') selected.value = await api.resumeRecording(selected.value.id)
    if (action === 'stop') selected.value = await api.stopRecording(selected.value.id)
    await loadRecordings()
    toast.info(`Recording ${action === 'stop' ? 'stopped' : action + 'd'}`, selected.value.name)
  } catch (cause) {
    error.value = errorMessage(cause, 'That recording action failed.')
  }
}

watch(actionType, (type) => {
  const action = actionMeta(type)
  stepDescription.value = action?.label ?? ''
  parameters.value = pretty(EXAMPLES[type] ?? {})
  stepError.value = ''
})

async function addStep() {
  if (!selected.value) return
  let parsed: Record<string, unknown>
  try {
    const value = JSON.parse(parameters.value || '{}') as unknown
    if (!value || Array.isArray(value) || typeof value !== 'object') {
      throw new Error('Parameters must be a JSON object.')
    }
    parsed = value as Record<string, unknown>
  } catch (cause) {
    stepError.value = errorMessage(cause, 'Parameters must be valid JSON.')
    return
  }
  addingStep.value = true
  stepError.value = ''
  try {
    selected.value = await api.addRecordedStep(selected.value.id, {
      actionType: actionType.value,
      description: stepDescription.value.trim() || actionLabel(actionType.value),
      parameters: parsed,
    })
    await loadRecordings()
    toast.success('Step recorded', actionLabel(actionType.value))
  } catch (cause) {
    const details = errorDetails(cause)
    stepError.value = details.length ? `${errorMessage(cause)} ${details.join(' ')}` : errorMessage(cause)
  } finally {
    addingStep.value = false
  }
}

async function removeStep(step: RecordedStepView) {
  if (!selected.value) return
  try {
    selected.value = await api.deleteRecordedStep(selected.value.id, step.id)
    await loadRecordings()
    toast.info('Step removed', step.description)
  } catch (cause) {
    error.value = errorMessage(cause, 'That step could not be removed.')
  }
}

async function saveWorkflow() {
  if (!selected.value) return
  if (!saveName.value.trim()) {
    saveError.value = 'Give the saved workflow a name.'
    return
  }
  saving.value = true
  saveError.value = ''
  try {
    const workflow = await api.saveRecording(selected.value.id, {
      name: saveName.value.trim(),
      description: saveDescription.value.trim() || undefined,
      category: saveCategory.value,
    })
    await loadRecordings()
    selected.value = await api.recording(selected.value.id)
    toast.success('Workflow drafted', 'Review and approve it in GhostReplay before a live run.')
    saveError.value = ''
    saveName.value = workflow.name
  } catch (cause) {
    const details = errorDetails(cause)
    saveError.value = details.length ? `${errorMessage(cause)} ${details.join(' ')}` : errorMessage(cause)
  } finally {
    saving.value = false
  }
}

async function removeRecording() {
  if (!selected.value) return
  deleteBusy.value = true
  try {
    await api.deleteRecording(selected.value.id)
    const deletedName = selected.value.name
    selected.value = null
    deleteOpen.value = false
    await loadRecordings()
    toast.info('Recording deleted', `${deletedName}. Any workflow already saved from it is preserved.`)
  } catch (cause) {
    error.value = errorMessage(cause, 'The recording could not be deleted.')
  } finally {
    deleteBusy.value = false
  }
}

onMounted(async () => {
  await session.loadActions().catch(() => undefined)
  if (session.directories.length === 0) await session.refreshDirectories().catch(() => undefined)
  await loadRecordings()
  if (recordings.value.length > 0) await select(recordings.value[0])
})
</script>

<template>
  <PageHeader
    eyebrow="GhostCapture"
    title="Show the shift once. Replay it later."
    description="Record an allowlisted sequence of file actions, inspect every step, and save it as a draft workflow. You still review and approve before anything runs."
  >
    <template #actions>
      <button class="btn btn--ghost btn--sm" type="button" :disabled="loading" @click="loadRecordings">
        <span v-if="loading" class="spinner" />
        <Icon v-else name="refresh" :size="15" />
        Refresh
      </button>
    </template>
  </PageHeader>

  <p v-if="error" class="capture-error" role="alert">{{ error }}</p>

  <div class="capture-notice">
    <Icon name="alert" :size="16" />
    <p>
      <strong>This version records file actions, not your browser screen.</strong>
      Browser and screen capture are not implemented, so GhostShift does not claim them. File-action recording is real:
      you add allowlisted steps, edit parameters, and replay them as a workflow.
    </p>
  </div>

  <section class="panel capture-start">
    <div class="capture-start__intro">
      <span class="eyebrow">New recording</span>
      <h2>Name the shift, choose a folder.</h2>
      <p>
        A folder is required for any step that reads or moves files. Starting a recording does not touch files.
      </p>
    </div>
    <form class="capture-start__form" @submit.prevent="start">
      <div class="field">
        <label for="recording-name">Recording name</label>
        <input id="recording-name" v-model="name" type="text" maxlength="120" placeholder="Tidy the April exports" />
      </div>
      <DirectoryPicker
        v-model="directoryId"
        label="Working folder (optional)"
        hint="Required before you can add file-reading or move steps."
      />
      <button class="btn btn--primary" type="submit" :disabled="starting">
        <span v-if="starting" class="spinner" />
        <Icon v-else name="record" :size="15" />
        Start recording
      </button>
    </form>
  </section>

  <section v-if="recordings.length" class="recording-strip" aria-label="Recordings">
    <button
      v-for="recording in recordings"
      :key="recording.id"
      class="recording-tab"
      :class="{ 'recording-tab--active': selected?.id === recording.id }"
      type="button"
      @click="select(recording)"
    >
      <span class="recording-tab__top">
        <span class="recording-tab__name">{{ recording.name }}</span>
        <StatusPill :status="recording.status" />
      </span>
      <span class="recording-tab__meta">
        {{ recording.steps.length }} {{ plural(recording.steps.length, 'step') }} · {{ relativeTime(recording.createdAt) }}
      </span>
    </button>
  </section>

  <template v-if="selected">
    <div class="split-aside capture-main">
      <section class="panel">
        <header class="recording-head">
          <div>
            <div class="row">
              <StatusPill :status="selected.status" dot />
              <span class="chip">FILE actions</span>
              <span v-if="selected.steps.length" class="chip">{{ selected.steps.length }} {{ plural(selected.steps.length, 'step') }}</span>
            </div>
            <h2>{{ selected.name }}</h2>
            <p class="mono">
              {{ selectedDirectory?.displayName || 'No folder attached' }} · started
              {{ humanDate(selected.createdAt) }}
            </p>
          </div>
          <div class="recording-head__controls">
            <button
              v-if="selected.status === 'ACTIVE'"
              class="btn btn--sm"
              type="button"
              @click="control('pause')"
            >
              <Icon name="pause" :size="13" /> Pause
            </button>
            <button
              v-if="selected.status === 'PAUSED'"
              class="btn btn--sm"
              type="button"
              @click="control('resume')"
            >
              <Icon name="play" :size="13" /> Resume
            </button>
            <button
              v-if="editable"
              class="btn btn--sm"
              type="button"
              @click="control('stop')"
            >
              <Icon name="record" :size="13" /> Stop
            </button>
            <button class="icon-btn" type="button" aria-label="Delete recording" @click="deleteOpen = true">
              <Icon name="trash" :size="15" />
            </button>
          </div>
        </header>

        <div class="recording-status notice" :class="{ 'notice--accent': editable }">
          <Icon :name="editable ? 'record' : 'check'" :size="15" class="notice__icon" />
          <span v-if="selected.status === 'ACTIVE'">Recording is active. Add steps below when you are ready.</span>
          <span v-else-if="selected.status === 'PAUSED'">Paused. Resume or keep adding steps deliberately.</span>
          <span v-else-if="selected.status === 'STOPPED'">Stopped. Review and edit the steps, then save a workflow.</span>
          <span v-else>Saved as a workflow. The recording is preserved as a record.</span>
        </div>

        <div v-if="selected.steps.length" class="table-wrap">
          <table class="table capture-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Action</th>
                <th>Description</th>
                <th>Parameters</th>
                <th>Gate</th>
                <th><span class="sr-only">Remove</span></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="step in selected.steps" :key="step.id">
                <td class="mono faint">{{ step.order }}</td>
                <td><span class="cell-main">{{ actionLabel(step.actionType) }}</span></td>
                <td class="muted small">{{ step.description }}</td>
                <td><code class="capture-params">{{ JSON.stringify(step.parameters) }}</code></td>
                <td>
                  <span v-if="step.requiresApproval" class="chip chip--accent">Approval</span>
                  <span v-else class="faint tiny">Automatic</span>
                </td>
                <td class="capture-table__remove">
                  <button
                    v-if="editable"
                    class="icon-btn"
                    type="button"
                    :aria-label="`Remove ${step.description}`"
                    @click="removeStep(step)"
                  >
                    <Icon name="trash" :size="14" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="panel__body">
          <EmptyState
            compact
            icon="record"
            title="No steps recorded yet"
            description="Add the first allowlisted action below. Nothing is executed while recording."
          />
        </div>

        <section v-if="editable" class="add-step">
          <header>
            <h3>Add a recorded step</h3>
            <p>Only the fixed action catalog is available. There is no arbitrary shell action.</p>
          </header>
          <form class="add-step__form" @submit.prevent="addStep">
            <div class="field">
              <label for="step-action">Action</label>
              <select id="step-action" v-model="actionType">
                <option v-for="action in session.actions" :key="action.type" :value="action.type">
                  {{ action.label }} ({{ action.risk }})
                </option>
              </select>
            </div>
            <div class="field">
              <label for="step-description">Description</label>
              <input id="step-description" v-model="stepDescription" type="text" maxlength="300" />
            </div>
            <div class="field add-step__params">
              <label for="step-parameters">Parameters (JSON)</label>
              <textarea id="step-parameters" v-model="parameters" rows="5" spellcheck="false" />
              <div class="add-step__hints">
                <span v-for="hint in parameterHints(selectedAction)" :key="hint" class="chip">{{ hint }}</span>
                <span v-if="!parameterHints(selectedAction).length" class="faint tiny">No parameters for this action.</span>
              </div>
            </div>
            <div class="add-step__side">
              <p v-if="selectedAction" class="muted tiny">{{ selectedAction.description }}</p>
              <p v-if="selectedAction?.requiresApproval" class="add-step__gate">
                <Icon name="shield" :size="13" />
                This action always pauses for approval.
              </p>
              <p v-if="!selected.directoryId" class="add-step__gate">
                <Icon name="alert" :size="13" />
                No folder attached. File actions will be refused.
              </p>
              <button class="btn btn--primary" type="submit" :disabled="addingStep">
                <span v-if="addingStep" class="spinner" />
                <Icon v-else name="plus" :size="14" />
                Add step
              </button>
            </div>
            <p v-if="stepError" class="capture-error add-step__error" role="alert">{{ stepError }}</p>
          </form>
        </section>
      </section>

      <aside class="stack">
        <section class="panel panel--accent">
          <header class="panel__head"><h2>Save as workflow</h2></header>
          <div v-if="canSave" class="panel__body stack">
            <div class="field">
              <label for="save-name">Workflow name</label>
              <input id="save-name" v-model="saveName" type="text" maxlength="120" />
            </div>
            <div class="field">
              <label for="save-description">Description</label>
              <textarea id="save-description" v-model="saveDescription" rows="3" maxlength="2000" placeholder="What this recording is for..." />
            </div>
            <div class="field">
              <label for="save-category">Category</label>
              <select id="save-category" v-model="saveCategory">
                <option v-for="category in categoryOptions" :key="category" :value="category">
                  {{ titleCase(category) }}
                </option>
              </select>
            </div>
            <p v-if="saveError" class="capture-error" role="alert">{{ saveError }}</p>
            <button class="btn btn--primary btn--block" type="button" :disabled="saving" @click="saveWorkflow">
              <span v-if="saving" class="spinner" />
              <Icon v-else name="archive" :size="14" />
              Save draft workflow
            </button>
            <p class="field__hint">
              The workflow starts in review. Approve it in GhostReplay before a live run. Dry runs are available
              immediately.
            </p>
          </div>
          <div v-else-if="selected.workflowId" class="panel__body stack">
            <p class="muted small">This recording was saved as a workflow.</p>
            <RouterLink :to="{ name: 'ghost-replay', query: { workflow: selected.workflowId } }" class="btn btn--sm btn--block">
              <Icon name="play" :size="14" />
              Open in GhostReplay
            </RouterLink>
          </div>
          <div v-else class="panel__body">
            <p class="muted small">Add at least one step before saving.</p>
          </div>
        </section>

        <section class="panel panel--quiet">
          <header class="panel__head"><h2>Capture rules</h2></header>
          <div class="panel__body capture-rules">
            <p><Icon name="shield" :size="13" /> Steps are validated against the action registry at capture time.</p>
            <p><Icon name="lock" :size="13" /> A folder is attached to the recording and used for replay authorization.</p>
            <p><Icon name="alert" :size="13" /> Recordings never contain credentials, page content, or keystrokes.</p>
            <p><Icon name="undo" :size="13" /> Saved workflows keep their version history even if the recording is deleted.</p>
          </div>
        </section>
      </aside>
    </div>
  </template>

  <EmptyState
    v-else-if="!loading"
    icon="record"
    title="No recording selected"
    description="Start a recording above or choose one from the strip. File actions are captured deliberately, not by watching your screen."
  />

  <ConfirmDialog
    :open="deleteOpen"
    title="Delete this recording?"
    description="This removes the recording and its captured steps only."
    confirm-label="Delete recording"
    tone="danger"
    :busy="deleteBusy"
    @close="deleteOpen = false"
    @confirm="removeRecording"
  >
    <div class="notice notice--warning">
      <Icon name="alert" :size="15" class="notice__icon" />
      <span>Any workflow already saved from this recording is preserved. No user files are touched.</span>
    </div>
  </ConfirmDialog>
</template>

<style scoped>
.capture-error {
  margin: 0 0 1rem;
  padding: 0.65rem 0.85rem;
  border: 1px solid color-mix(in srgb, var(--danger) 30%, var(--line));
  border-left: 2px solid var(--danger);
  border-radius: var(--radius-sm);
  color: var(--danger);
  font-size: 0.78rem;
}
.capture-notice {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  margin-bottom: 1.2rem;
  padding: 0.8rem 1rem;
  border: 1px solid color-mix(in srgb, var(--warn) 22%, var(--line));
  border-left: 2px solid var(--warn);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--warn) 4%, transparent);
  color: var(--text-muted);
}
.capture-notice .icon {
  color: var(--warn);
  margin-top: 0.1rem;
  flex: none;
}
.capture-notice p {
  margin: 0;
  font-size: 0.78rem;
  line-height: 1.55;
}
.capture-notice strong {
  color: var(--text);
}
.capture-start {
  display: grid;
  grid-template-columns: minmax(230px, 0.75fr) minmax(0, 1.5fr);
  gap: 1.8rem;
  padding: 1.4rem 1.5rem;
  margin-bottom: 1.2rem;
}
.capture-start__intro h2 {
  margin: 0.45rem 0 0;
  font-family: var(--font-display);
  font-size: clamp(1.3rem, 2vw, 1.65rem);
  font-weight: 470;
  letter-spacing: -0.02em;
}
.capture-start__intro p {
  margin: 0.55rem 0 0;
  max-width: 40ch;
  color: var(--text-muted);
  font-size: 0.8rem;
  line-height: 1.6;
}
.capture-start__form {
  display: grid;
  grid-template-columns: 1fr 1.3fr auto;
  gap: 0.75rem;
  align-items: end;
}
.recording-strip {
  display: flex;
  gap: 0.5rem;
  overflow-x: auto;
  padding-bottom: 0.3rem;
  margin-bottom: 1.2rem;
}
.recording-tab {
  flex: 0 0 220px;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0.6rem 0.7rem;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text);
  text-align: left;
}
.recording-tab:hover {
  border-color: var(--line-strong);
}
.recording-tab--active {
  border-color: color-mix(in srgb, var(--accent) 35%, var(--line));
  background: color-mix(in srgb, var(--accent) 6%, var(--surface));
}
.recording-tab__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}
.recording-tab__name {
  font-size: 0.77rem;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recording-tab__meta {
  font-family: var(--font-mono);
  font-size: 0.58rem;
  color: var(--text-faint);
}
.recording-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.2rem 1.3rem;
  border-bottom: 1px solid var(--line);
}
.recording-head h2 {
  margin: 0.5rem 0 0.2rem;
  font-family: var(--font-display);
  font-size: 1.55rem;
  font-weight: 470;
  letter-spacing: -0.02em;
}
.recording-head p {
  margin: 0;
  color: var(--text-faint);
  font-size: 0.62rem;
  overflow-wrap: anywhere;
}
.recording-head__controls {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}
.recording-status {
  margin: 0.8rem 1rem 0;
}
.capture-params {
  font-size: 0.62rem;
  color: var(--text-faint);
  overflow-wrap: anywhere;
  display: block;
  max-width: 240px;
}
.capture-table__remove {
  text-align: right;
}
.add-step {
  border-top: 1px solid var(--line);
  padding: 1.1rem 1.3rem 1.3rem;
}
.add-step header h3 {
  margin: 0;
  font-size: 0.82rem;
  font-weight: 600;
}
.add-step header p {
  margin: 0.25rem 0 0.9rem;
  font-size: 0.73rem;
  color: var(--text-faint);
}
.add-step__form {
  display: grid;
  grid-template-columns: minmax(160px, 0.7fr) minmax(0, 1fr) minmax(190px, 0.6fr);
  gap: 0.8rem;
  align-items: start;
}
.add-step__params {
  grid-column: 1 / 3;
}
.add-step textarea {
  min-height: 7rem;
  font-family: var(--font-mono);
  font-size: 0.72rem;
}
.add-step__hints {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
}
.add-step__side {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding-top: 1.4rem;
}
.add-step__side p {
  margin: 0;
}
.add-step__side .btn {
  margin-top: auto;
}
.add-step__gate {
  display: flex;
  align-items: flex-start;
  gap: 0.3rem;
  color: var(--warn);
  font-size: 0.7rem;
  line-height: 1.4;
}
.add-step__gate .icon {
  margin-top: 0.05rem;
  flex: none;
}
.add-step__error {
  grid-column: 1 / -1;
  margin: 0;
}
.capture-rules {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}
.capture-rules p {
  display: flex;
  align-items: flex-start;
  gap: 0.4rem;
  margin: 0;
  font-size: 0.73rem;
  line-height: 1.45;
  color: var(--text-muted);
}
.capture-rules .icon {
  margin-top: 0.06rem;
  color: var(--text-faint);
  flex: none;
}

@media (max-width: 1050px) {
  .capture-start {
    grid-template-columns: 1fr;
    gap: 1.2rem;
  }
  .capture-start__form {
    grid-template-columns: 1fr 1.2fr;
  }
  .capture-start__form .btn {
    grid-column: 2;
    justify-self: start;
  }
  .add-step__form {
    grid-template-columns: 1fr 1fr;
  }
  .add-step__params {
    grid-column: 1 / -1;
  }
  .add-step__side {
    grid-column: 1 / -1;
    padding-top: 0;
  }
}
@media (max-width: 700px) {
  .capture-start__form {
    grid-template-columns: 1fr;
  }
  .capture-start__form .btn {
    grid-column: auto;
  }
  .recording-head {
    flex-direction: column;
  }
  .add-step__form {
    grid-template-columns: 1fr;
  }
  .add-step__params {
    grid-column: auto;
  }
  .capture-table__remove {
    text-align: left;
  }
}
</style>
