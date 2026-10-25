<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '@/api/client'
import type {
  AiWorkflowDraft,
  WorkflowDefinition,
  WorkflowDetail,
  WorkflowInput,
  WorkflowSummary,
  WorkflowValidation,
} from '@/api/types'
import { errorDetails, errorMessage } from '@/lib/errors'
import { humanDate, plural, relativeTime, titleCase } from '@/lib/format'
import { useSessionStore } from '@/stores/session'
import { useToastStore } from '@/stores/toast'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import DirectoryPicker from '@/components/DirectoryPicker.vue'
import EmptyState from '@/components/EmptyState.vue'
import Icon from '@/components/Icon.vue'
import Modal from '@/components/Modal.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'

type StatusAction = 'approve' | 'disable' | 'archive' | 'restore'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const toast = useToastStore()

const workflows = ref<WorkflowSummary[]>([])
const selected = ref<WorkflowDetail | null>(null)
const loading = ref(false)
const detailLoading = ref(false)
const error = ref('')
const includeArchived = ref(false)

const running = ref(false)
const runError = ref('')
const lastRunId = ref('')
const directoryInputs = ref<Record<string, number | null>>({})
const textInputs = ref<Record<string, string>>({})
const numberInputs = ref<Record<string, string>>({})
const booleanInputs = ref<Record<string, boolean>>({})

const editorOpen = ref(false)
const editorMode = ref<'create' | 'edit'>('create')
const editorName = ref('')
const editorDescription = ref('')
const editorCategory = ref('GENERAL')
const editorJson = ref('')
const editorNotice = ref('')
const editorError = ref('')
const editorValidation = ref<WorkflowValidation | null>(null)
const editingValidating = ref(false)
const editingSaving = ref(false)

const draftPrompt = ref('')
const draftDirectoryId = ref<number | null>(null)
const drafting = ref(false)
const draftError = ref('')
const draftResult = ref<AiWorkflowDraft | null>(null)

const pendingAction = ref<StatusAction | null>(null)
const actionBusy = ref(false)

const categories = ['GENERAL', 'FILE_ORGANIZATION', 'LOG_ANALYSIS', 'DEVELOPER', 'CUSTOM']

const steps = computed(() => selected.value?.definition.steps ?? [])
const inputDefs = computed(() => selected.value?.definition.inputs ?? [])
const validationErrors = computed(() => selected.value?.validation.errors ?? [])
const validationWarnings = computed(() => selected.value?.validation.warnings ?? [])
const requiredPermissions = computed(() => selected.value?.validation.requiredPermissions ?? [])
const riskSummary = computed(() => selected.value?.validation.riskSummary ?? [])
const versions = computed(() => selected.value?.versions ?? [])

const dryBlocked = computed(() => {
  if (!selected.value) return 'Select a workflow first.'
  if (selected.value.archived) return 'Restore this workflow before running it.'
  if (!selected.value.validation.valid) return 'Fix the validation errors before running this workflow.'
  return ''
})

const liveBlocked = computed(() => {
  if (dryBlocked.value) return dryBlocked.value
  if (selected.value?.status !== 'APPROVED') {
    return 'Approve this workflow before a live run. Dry runs are available while it is under review.'
  }
  return ''
})

const canApprove = computed(
  () =>
    Boolean(selected.value) &&
    !selected.value?.archived &&
    selected.value?.status !== 'APPROVED' &&
    Boolean(selected.value?.validation.valid),
)

const emptyDefinition = (): WorkflowDefinition => ({
  schemaVersion: 1,
  name: '',
  description: '',
  steps: [
    {
      id: 'list',
      type: 'LIST_FILES',
      description: 'List the files in the chosen folder.',
      parameters: { recursive: false, maxFiles: 100 },
      enabled: true,
    },
  ],
  inputs: [
    {
      name: 'directoryId',
      label: 'Folder',
      type: 'DIRECTORY',
      required: true,
      description: 'The registered folder this workflow may read.',
    },
  ],
  requiresApproval: true,
  failurePolicy: 'STOP',
})

const confirmCopy = computed(() => {
  switch (pendingAction.value) {
    case 'approve':
      return {
        title: 'Approve this workflow?',
        description: selected.value?.name ?? '',
        label: 'Approve workflow',
        tone: 'primary' as const,
        note: 'A live run will be allowed after this. Every run still shows the exact steps, and file moves keep the backend approval gate and conflict checks.',
      }
    case 'disable':
      return {
        title: 'Disable live runs?',
        description: selected.value?.name ?? '',
        label: 'Disable workflow',
        tone: 'primary' as const,
        note: 'The workflow and its version history are preserved. Dry runs stay available, but live runs will be refused until you approve it again.',
      }
    case 'archive':
      return {
        title: 'Archive this workflow?',
        description: selected.value?.name ?? '',
        label: 'Archive workflow',
        tone: 'danger' as const,
        note: 'Archiving hides the workflow from the active list. Existing runs, reports, and version history are preserved. You can restore it later.',
      }
    case 'restore':
      return {
        title: 'Restore this workflow?',
        description: selected.value?.name ?? '',
        label: 'Restore workflow',
        tone: 'primary' as const,
        note: 'It returns to review. A workflow is never silently re-approved when it comes back from the archive.',
      }
    default:
      return { title: '', description: '', label: 'Confirm', tone: 'primary' as const, note: '' }
  }
})

function statusMeaning(status?: string): string {
  switch (status) {
    case 'APPROVED':
      return 'Approved for live runs. Dry runs are also available. File moves still pause at the backend approval gate.'
    case 'READY_FOR_REVIEW':
      return 'Validated and waiting for your approval. Nothing live can run until you approve it.'
    case 'DRAFT':
      return 'Draft. Fix validation issues, then approve it for review or live use.'
    case 'DISABLED':
      return 'Live runs are disabled. The definition and history remain, and dry runs are still available.'
    case 'ARCHIVED':
      return 'Archived. Restore it before running or approving it.'
    default:
      return 'Review the definition and validation before running this workflow.'
  }
}

function definitionText(definition: WorkflowDefinition): string {
  return JSON.stringify(definition, null, 2)
}

function resetInputs(definition: WorkflowDefinition) {
  const nextDirectories: Record<string, number | null> = {}
  const nextText: Record<string, string> = {}
  const nextNumbers: Record<string, string> = {}
  const nextBooleans: Record<string, boolean> = {}
  for (const input of definition.inputs ?? []) {
    if (input.type === 'DIRECTORY') nextDirectories[input.name] = null
    else if (input.type === 'NUMBER') nextNumbers[input.name] = ''
    else if (input.type === 'BOOLEAN') nextBooleans[input.name] = false
    else nextText[input.name] = ''
  }
  directoryInputs.value = nextDirectories
  textInputs.value = nextText
  numberInputs.value = nextNumbers
  booleanInputs.value = nextBooleans
}

function actionLabel(type: string): string {
  return session.actions.find((action) => action.type === type)?.label ?? titleCase(type)
}

function actionRisk(type: string): string {
  return session.actions.find((action) => action.type === type)?.risk ?? 'LOW'
}

function actionPermission(type: string): string {
  return session.actions.find((action) => action.type === type)?.permission ?? 'Read access'
}

function inputHint(input: WorkflowInput): string {
  const parts = [input.required ? 'Required.' : 'Optional.', input.description].filter(Boolean)
  return parts.join(' ')
}

function defaultInputValue(input: WorkflowInput): string {
  if (input.type === 'NUMBER') return numberInputs.value[input.name] ?? ''
  return textInputs.value[input.name] ?? ''
}

async function loadWorkflows(keepSelection = true) {
  loading.value = true
  error.value = ''
  try {
    const list = await api.workflows(includeArchived.value)
    workflows.value = list
    const queryId = typeof route.query.workflow === 'string' ? route.query.workflow : ''
    const wanted =
      (keepSelection ? selected.value?.id : '') ||
      queryId ||
      (list.length > 0 ? list[0].id : '')
    if (wanted && list.some((item) => item.id === wanted)) {
      await open(wanted)
    } else if (list.length > 0) {
      await open(list[0].id)
    } else {
      selected.value = null
      resetInputs({ ...emptyDefinition(), inputs: [] })
    }
  } catch (cause) {
    error.value = errorMessage(cause, 'Workflows could not be loaded.')
  } finally {
    loading.value = false
  }
}

async function open(id: string) {
  detailLoading.value = true
  error.value = ''
  try {
    const detail = await api.workflow(id)
    selected.value = detail
    lastRunId.value = ''
    runError.value = ''
    resetInputs(detail.definition)
  } catch (cause) {
    error.value = errorMessage(cause, 'That workflow could not be opened.')
  } finally {
    detailLoading.value = false
  }
}

function buildInputs(): Record<string, unknown> {
  if (!selected.value) throw new Error('Select a workflow first.')
  const result: Record<string, unknown> = {}
  for (const input of inputDefs.value) {
    if (input.type === 'DIRECTORY') {
      const value = directoryInputs.value[input.name]
      if (value === null || value === undefined) {
        if (input.required) throw new Error(`Choose a folder for "${input.label}".`)
        continue
      }
      result[input.name] = value
    } else if (input.type === 'NUMBER') {
      const raw = (numberInputs.value[input.name] ?? '').trim()
      if (!raw) {
        if (input.required) throw new Error(`Enter a number for "${input.label}".`)
        continue
      }
      const value = Number(raw)
      if (!Number.isFinite(value)) throw new Error(`"${input.label}" must be a number.`)
      result[input.name] = value
    } else if (input.type === 'BOOLEAN') {
      result[input.name] = Boolean(booleanInputs.value[input.name])
    } else {
      const raw = textInputs.value[input.name] ?? ''
      if (!raw.trim() && input.required) throw new Error(`Enter a value for "${input.label}".`)
      if (raw.trim()) result[input.name] = raw
    }
  }
  return result
}

async function startRun(dryRun: boolean) {
  if (!selected.value) return
  if (dryRun ? dryBlocked.value : liveBlocked.value) {
    runError.value = dryRun ? dryBlocked.value : liveBlocked.value
    return
  }
  runError.value = ''
  running.value = true
  try {
    const payload = buildInputs()
    const run = await api.runWorkflow(selected.value.id, { inputs: payload, dryRun })
    lastRunId.value = run.id
    toast.success(
      dryRun ? 'Dry run queued' : 'Live run queued',
      dryRun ? 'No user files will be changed.' : 'The approval gate stays active for risky steps.',
    )
    await router.push({ name: 'run-detail', params: { runId: run.id } })
  } catch (cause) {
    const details = errorDetails(cause)
    runError.value = details.length ? `${errorMessage(cause)} ${details.join(' ')}` : errorMessage(cause)
  } finally {
    running.value = false
  }
}

function openCreate() {
  const definition = emptyDefinition()
  editorMode.value = 'create'
  editorName.value = ''
  editorDescription.value = ''
  editorCategory.value = 'GENERAL'
  editorJson.value = definitionText(definition)
  editorNotice.value = 'New workflows start in draft or review. A live run is only possible after you approve it.'
  editorValidation.value = null
  editorError.value = ''
  editorOpen.value = true
}

function openEdit() {
  if (!selected.value) return
  editorMode.value = 'edit'
  editorName.value = selected.value.name
  editorDescription.value = selected.value.description
  editorCategory.value = selected.value.category
  editorJson.value = definitionText(selected.value.definition)
  editorNotice.value =
    selected.value.status === 'APPROVED'
      ? 'This workflow is approved. Saving a changed definition increments the version and returns it to review.'
      : 'Saving a changed definition increments the version. The existing runs keep the version they used.'
  editorValidation.value = selected.value.validation
  editorError.value = ''
  editorOpen.value = true
}

function parseEditorDefinition(): WorkflowDefinition {
  let parsed: unknown
  try {
    parsed = JSON.parse(editorJson.value || '{}')
  } catch (cause) {
    throw new Error(errorMessage(cause, 'The definition must be valid JSON.'))
  }
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new Error('The workflow definition must be a JSON object.')
  }
  const definition = parsed as WorkflowDefinition
  const name = editorName.value.trim() || definition.name || ''
  return {
    ...definition,
    schemaVersion: 1,
    name,
    description: editorDescription.value,
    steps: Array.isArray(definition.steps) ? definition.steps : [],
    inputs: Array.isArray(definition.inputs) ? definition.inputs : [],
    requiresApproval: true,
    failurePolicy: definition.failurePolicy === 'CONTINUE_SAFE' ? 'CONTINUE_SAFE' : 'STOP',
  }
}

async function validateEditor() {
  editingValidating.value = true
  editorError.value = ''
  try {
    const candidate = parseEditorDefinition()
    editorValidation.value = await api.validateWorkflow(candidate)
    if (editorValidation.value.valid) {
      toast.success('Definition is valid', 'Every step uses the fixed action registry and satisfies its parameter rules.')
    } else {
      const count = editorValidation.value.errors?.length ?? 0
      toast.warning('Validation found issues', `${count} ${plural(count, 'error')} must be fixed before a run.`)
    }
  } catch (cause) {
    const details = errorDetails(cause)
    editorError.value = details.length ? `${errorMessage(cause)} ${details.join(' ')}` : errorMessage(cause)
  } finally {
    editingValidating.value = false
  }
}

async function saveEditor() {
  editingSaving.value = true
  editorError.value = ''
  try {
    const definition = parseEditorDefinition()
    const validation = !editorValidation.value?.valid ? await api.validateWorkflow(definition) : editorValidation.value
    editorValidation.value = validation
    const payload = {
      name: editorName.value.trim() || definition.name,
      description: editorDescription.value,
      category: editorCategory.value,
      definition,
    }
    const saved =
      editorMode.value === 'create'
        ? await api.createWorkflow(payload)
        : await api.updateWorkflow(selected.value!.id, payload)
    editorOpen.value = false
    await loadWorkflows(false)
    await open(saved.id)
    toast.success(
      editorMode.value === 'create' ? 'Workflow saved' : 'Workflow version saved',
      saved.validation.valid
        ? `Version ${saved.currentVersion} is ${saved.status.replaceAll('_', ' ').toLowerCase()}.`
        : `Version ${saved.currentVersion} was saved as a draft because validation still has errors.`,
    )
  } catch (cause) {
    const details = errorDetails(cause)
    editorError.value = details.length ? `${errorMessage(cause)} ${details.join(' ')}` : errorMessage(cause)
  } finally {
    editingSaving.value = false
  }
}

function openDraftInEditor(draft: AiWorkflowDraft) {
  editorMode.value = 'create'
  editorName.value = draft.definition.name
  editorDescription.value = draft.definition.description
  editorCategory.value = 'CUSTOM'
  editorJson.value = definitionText(draft.definition)
  editorValidation.value = draft.validation
  editorNotice.value = draft.explanation
  editorError.value = ''
  editorOpen.value = true
}

async function draftWorkflow() {
  if (!draftPrompt.value.trim()) {
    draftError.value = 'Describe the task you want the local model to draft.'
    return
  }
  drafting.value = true
  draftError.value = ''
  draftResult.value = null
  try {
    const draft = await api.draftWorkflow(draftPrompt.value.trim(), draftDirectoryId.value ?? undefined)
    draftResult.value = draft
    openDraftInEditor(draft)
    toast.success('Draft returned', 'Review the JSON and validation, then save it as a workflow. Nothing was executed.')
  } catch (cause) {
    const details = errorDetails(cause)
    draftError.value = details.length ? `${errorMessage(cause)} ${details.join(' ')}` : errorMessage(cause)
  } finally {
    drafting.value = false
  }
}

function requestAction(action: StatusAction) {
  pendingAction.value = action
}

async function confirmAction() {
  if (!pendingAction.value || !selected.value) return
  actionBusy.value = true
  error.value = ''
  try {
    const action = pendingAction.value
    if (action === 'approve') {
      await api.setWorkflowStatus(selected.value.id, 'APPROVED')
      toast.success('Workflow approved', 'Live runs are now allowed. Each run is still recorded and gated.')
    } else if (action === 'disable') {
      await api.setWorkflowStatus(selected.value.id, 'DISABLED')
      toast.info('Live runs disabled', 'Dry runs remain available and history is unchanged.')
    } else if (action === 'archive') {
      includeArchived.value = true
      await api.archiveWorkflow(selected.value.id)
      toast.info('Workflow archived', 'Runs, reports, and versions are preserved.')
    } else if (action === 'restore') {
      await api.restoreWorkflow(selected.value.id)
      toast.success('Workflow restored', 'It is back in review, not silently approved.')
    }
    pendingAction.value = null
    await loadWorkflows(true)
  } catch (cause) {
    const details = errorDetails(cause)
    error.value = details.length ? `${errorMessage(cause)} ${details.join(' ')}` : errorMessage(cause)
    pendingAction.value = null
  } finally {
    actionBusy.value = false
  }
}

async function runAgain() {
  if (lastRunId.value) await router.push({ name: 'run-detail', params: { runId: lastRunId.value } })
}

onMounted(async () => {
  if (session.directories.length === 0) await session.refreshDirectories().catch(() => undefined)
  await session.loadActions().catch(() => undefined)
  if (typeof route.query.prompt === 'string') draftPrompt.value = route.query.prompt
  await loadWorkflows(true)
})
</script>

<template>
  <PageHeader
    eyebrow="GhostReplay"
    title="Replay only what you have reviewed."
    description="Workflows are versioned, validated against a closed action registry, and approved by a human. Dry runs prove the path; live runs keep the same gates."
  >
    <template #actions>
      <label class="check archived-toggle">
        <input v-model="includeArchived" type="checkbox" @change="loadWorkflows(false)" />
        Show archived
      </label>
      <button class="btn btn--ghost btn--sm" type="button" :disabled="loading" @click="loadWorkflows(true)">
        <span v-if="loading" class="spinner" />
        <Icon v-else name="refresh" :size="15" />
        Refresh
      </button>
      <button class="btn btn--primary btn--sm" type="button" @click="openCreate">
        <Icon name="plus" :size="14" />
        New workflow
      </button>
    </template>
  </PageHeader>

  <p v-if="error" class="replay-error" role="alert">{{ error }}</p>

  <section v-if="workflows.length" class="workflow-strip" aria-label="Workflows">
    <button
      v-for="workflow in workflows"
      :key="workflow.id"
      class="workflow-tab"
      :class="{ 'workflow-tab--active': selected?.id === workflow.id }"
      type="button"
      @click="open(workflow.id)"
    >
      <span class="workflow-tab__top">
        <span class="workflow-tab__name">{{ workflow.name }}</span>
        <StatusPill :status="workflow.status" />
      </span>
      <span class="workflow-tab__meta">
        v{{ workflow.currentVersion }} · {{ workflow.stepCount }} {{ plural(workflow.stepCount, 'step') }} ·
        {{ relativeTime(workflow.updatedAt) }}
      </span>
    </button>
  </section>

  <div v-if="detailLoading" class="replay-loading">
    <div class="skeleton" style="width: 36%; height: 1.1rem" />
    <div class="skeleton" style="width: 100%; height: 21rem" />
  </div>

  <div v-else-if="selected" class="split-aside replay-main">
    <div class="stack">
      <section class="panel">
        <header class="workflow-head">
          <div class="workflow-head__copy">
            <div class="row">
              <StatusPill :status="selected.status" dot />
              <span class="chip">v{{ selected.currentVersion }}</span>
              <span class="chip">{{ steps.length }} {{ plural(steps.length, 'step') }}</span>
              <span v-if="selected.archived" class="chip">Archived</span>
            </div>
            <h2>{{ selected.name }}</h2>
            <p class="workflow-head__desc">{{ selected.description || 'No description was written for this workflow.' }}</p>
            <p class="mono workflow-head__meta">
              Updated {{ humanDate(selected.updatedAt) }} · created {{ humanDate(selected.createdAt) }} ·
              {{ titleCase(selected.category) }}
            </p>
          </div>
          <div class="workflow-head__actions">
            <button v-if="!selected.archived" class="btn btn--sm" type="button" @click="openEdit">
              <Icon name="sliders" :size="14" />
              Edit definition
            </button>
            <button
              v-if="canApprove"
              class="btn btn--sm btn--primary"
              type="button"
              :disabled="actionBusy"
              @click="requestAction('approve')"
            >
              <Icon name="check" :size="14" />
              Approve
            </button>
            <button
              v-if="selected.status === 'APPROVED' && !selected.archived"
              class="btn btn--sm"
              type="button"
              :disabled="actionBusy"
              @click="requestAction('disable')"
            >
              <Icon name="pause" :size="13" />
              Disable
            </button>
            <button
              v-if="selected.archived"
              class="btn btn--sm"
              type="button"
              :disabled="actionBusy"
              @click="requestAction('restore')"
            >
              <Icon name="undo" :size="14" />
              Restore
            </button>
            <button
              v-else
              class="icon-btn"
              type="button"
              aria-label="Archive workflow"
              :disabled="actionBusy"
              @click="requestAction('archive')"
            >
              <Icon name="archive" :size="15" />
            </button>
          </div>
        </header>

        <div class="workflow-state" :class="{ 'workflow-state--ready': selected.status === 'APPROVED' }">
          <Icon :name="selected.status === 'APPROVED' ? 'shield' : 'eye'" :size="15" />
          <span>{{ statusMeaning(selected.status) }}</span>
        </div>

        <div class="validation-block">
          <div class="validation-head">
            <h3>Validation</h3>
            <StatusPill
              :tone="selected.validation.valid ? 'success' : 'danger'"
              :label="selected.validation.valid ? 'Valid' : `${validationErrors.length} ${plural(validationErrors.length, 'error')}`"
            />
          </div>

          <ul v-if="validationErrors.length" class="issue-list issue-list--danger">
            <li v-for="issue in validationErrors" :key="issue">
              <Icon name="alert" :size="13" />
              {{ issue }}
            </li>
          </ul>
          <ul v-if="validationWarnings.length" class="issue-list issue-list--warn">
            <li v-for="warning in validationWarnings" :key="warning">
              <Icon name="eye" :size="13" />
              {{ warning }}
            </li>
          </ul>
          <p v-if="!validationErrors.length && !validationWarnings.length" class="muted small">
            No validation errors or warnings. This definition is limited to the fixed action catalog.
          </p>

          <div class="permission-row">
            <span v-for="permission in requiredPermissions" :key="permission" class="chip">{{ permission }}</span>
            <span v-if="!requiredPermissions.length" class="chip">No registered-folder permission needed</span>
          </div>
          <div v-if="riskSummary.length" class="risk-row">
            <span class="eyebrow">Action risks</span>
            <span v-for="risk in riskSummary" :key="risk" class="risk-chip">{{ risk }}</span>
          </div>
        </div>

        <div class="replay-steps">
          <header class="panel__head panel__head--inner">
            <h2>Steps</h2>
            <span class="panel__hint">Executed in order</span>
          </header>
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Action</th>
                  <th>Description</th>
                  <th>Parameters</th>
                  <th>Gate</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(step, index) in steps" :key="step.id">
                  <td class="mono faint">{{ index + 1 }}</td>
                  <td>
                    <span class="cell-main">{{ actionLabel(step.type) }}</span>
                    <span class="cell-sub mono">{{ step.id }} · {{ actionPermission(step.type) }}</span>
                  </td>
                  <td class="muted small">{{ step.description || 'No description' }}</td>
                  <td>
                    <code class="step-params">{{ JSON.stringify(step.parameters ?? {}) }}</code>
                  </td>
                  <td>
                    <span v-if="!step.enabled" class="chip">Disabled</span>
                    <span
                      v-else
                      class="chip"
                      :class="{ 'chip--accent': actionRisk(step.type) !== 'LOW' }"
                    >
                      {{ actionRisk(step.type) }} risk
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <details class="version-block">
          <summary>Version history <span class="faint">({{ versions.length }})</span></summary>
          <div class="version-list">
            <div v-for="version in versions" :key="version.id" class="version-row">
              <span class="version-row__number mono">v{{ version.version }}</span>
              <div class="version-row__copy">
                <strong>{{ humanDate(version.createdAt) }}</strong>
                <span>
                  {{ version.validation.valid ? 'Validated' : 'Validation errors' }} ·
                  {{ version.validation.requiredPermissions?.length ?? 0 }}
                  {{ plural(version.validation.requiredPermissions?.length ?? 0, 'permission') }}
                </span>
              </div>
              <StatusPill :tone="version.validation.valid ? 'success' : 'danger'" :label="version.validation.valid ? 'valid' : 'invalid'" />
            </div>
          </div>
        </details>
      </section>

      <section class="panel panel--quiet replay-rule">
        <div class="panel__body replay-rule__body">
          <Icon name="shield" :size="18" />
          <div>
            <h3>The execution boundary is boring on purpose.</h3>
            <p>
              The local model can propose a workflow, but it never chooses from actions outside this registry, never
              supplies shell commands, and never authorizes a move. The engine checks folder boundaries, refuses
              overwrites and deletes, and records every step. A live run requires an approved definition and re-checks
              the folder permission at execution time.
            </p>
          </div>
        </div>
      </section>
    </div>

    <aside class="stack">
      <section class="panel run-panel">
        <header class="panel__head">
          <h2>Run this workflow</h2>
          <span class="chip">{{ selected.status === 'APPROVED' ? 'Live enabled' : 'Review only' }}</span>
        </header>
        <div class="panel__body stack">
          <div v-if="inputDefs.length" class="stack stack--sm">
            <template v-for="input in inputDefs" :key="input.name">
              <DirectoryPicker
                v-if="input.type === 'DIRECTORY'"
                v-model="directoryInputs[input.name]"
                :label="input.label || input.name"
                :hint="inputHint(input)"
              />
              <div v-else-if="input.type === 'TEXT'" class="field">
                <label :for="`input-${input.name}`">{{ input.label || input.name }}</label>
                <input
                  :id="`input-${input.name}`"
                  v-model="textInputs[input.name]"
                  type="text"
                  maxlength="4000"
                  :placeholder="input.description"
                />
                <p class="field__hint">{{ input.required ? 'Required' : 'Optional' }}</p>
              </div>
              <div v-else-if="input.type === 'NUMBER'" class="field">
                <label :for="`input-${input.name}`">{{ input.label || input.name }}</label>
                <input
                  :id="`input-${input.name}`"
                  v-model="numberInputs[input.name]"
                  type="number"
                  step="any"
                />
                <p class="field__hint">{{ inputHint(input) }}</p>
              </div>
              <label v-else class="check run-panel__check">
                <input v-model="booleanInputs[input.name]" type="checkbox" />
                {{ input.label || input.name }}
                <span v-if="input.description" class="faint">— {{ input.description }}</span>
              </label>
            </template>
          </div>
          <p v-else class="muted small">This workflow takes no inputs.</p>

          <p v-if="runError" class="replay-error" role="alert">{{ runError }}</p>

          <div class="run-actions">
            <button
              class="btn btn--primary btn--block"
              type="button"
              :disabled="running || Boolean(dryBlocked)"
              :title="dryBlocked || 'Run every step without changing user files.'"
              @click="startRun(true)"
            >
              <span v-if="running" class="spinner" />
              <Icon v-else name="play" :size="14" />
              Start dry run
            </button>
            <button
              class="btn btn--block"
              type="button"
              :disabled="running || Boolean(liveBlocked)"
              :title="liveBlocked || 'Run the approved workflow and record every action.'"
              @click="startRun(false)"
            >
              <Icon name="shield" :size="14" />
              Start live run
            </button>
          </div>
          <p v-if="liveBlocked" class="field__hint">{{ liveBlocked }}</p>
          <p v-if="lastRunId" class="run-panel__last">
            <button class="panel__link" type="button" @click="runAgain">
              Open the last run <Icon name="arrow" :size="13" />
            </button>
          </p>
          <p class="field__hint">
            Dry runs exercise the same planner and validation with simulated actions. The backend still refuses
            folders without organize permission for live move workflows.
          </p>
        </div>
      </section>

      <section class="panel panel--accent">
        <header class="panel__head">
          <h2>Draft with GhostBrain</h2>
          <span class="chip" :class="{ 'chip--accent': session.aiOnline }">
            {{ session.aiOnline ? session.ai?.model : 'Offline' }}
          </span>
        </header>
        <div class="panel__body stack">
          <div class="field">
            <label for="draft-prompt">Describe the repeatable task</label>
            <textarea
              id="draft-prompt"
              v-model="draftPrompt"
              rows="4"
              maxlength="4000"
              placeholder="List the log files in a folder, group repeated errors, and save a diagnostic report. Do not move files."
            />
            <p class="field__hint">The model receives your description and the action catalog, not your files.</p>
          </div>
          <DirectoryPicker
            v-model="draftDirectoryId"
            label="Folder context (optional)"
            hint="Used only to add a required directory input to the draft."
          />
          <p v-if="draftError" class="replay-error" role="alert">{{ draftError }}</p>
          <p v-if="draftResult" class="draft-result">
            <Icon name="brain" :size="14" />
            Draft returned. Review it in the editor before saving.
          </p>
          <button class="btn btn--primary btn--block" type="button" :disabled="drafting" @click="draftWorkflow">
            <span v-if="drafting" class="spinner" />
            <Icon v-else name="spark" :size="14" />
            Draft a workflow
          </button>
          <p v-if="!session.aiOnline" class="field__hint">
            GhostBrain is not ready, so drafting will explain the fallback. Manual editing stays available.
          </p>
          <RouterLink to="/brain" class="panel__link">Model configuration and guardrails <Icon name="arrow" :size="13" /></RouterLink>
        </div>
      </section>

      <section class="panel panel--quiet">
        <header class="panel__head"><h2>Workflow rules</h2></header>
        <div class="panel__body workflow-rules">
          <p><Icon name="shield" :size="13" /> Every definition is validated against the closed action registry.</p>
          <p><Icon name="lock" :size="13" /> Live runs need an approved workflow and a folder with organize permission for moves.</p>
          <p><Icon name="undo" :size="13" /> Approved edits reset to review; no silent change to an approved automation.</p>
          <p><Icon name="clock" :size="13" /> Runs are version-pinned. Editing a workflow never changes a recorded run.</p>
        </div>
      </section>
    </aside>
  </div>

  <EmptyState
    v-else-if="!loading"
    icon="play"
    title="No workflow selected"
    description="Example workflows are seeded at startup, but no run history is fabricated. Choose one, inspect the steps, and start with a dry run."
  >
    <button class="btn btn--sm" type="button" @click="openCreate">
      <Icon name="plus" :size="14" />
      Create a workflow
    </button>
  </EmptyState>

  <Modal
    :open="editorOpen"
    :title="editorMode === 'create' ? 'New workflow' : 'Edit workflow definition'"
    :subtitle="editorMode === 'create' ? 'Start from a validated template or an AI draft. Saving does not run anything.' : 'A changed definition creates a new version and returns an approved workflow to review.'"
    wide
    @close="editorOpen = false"
  >
    <div class="editor">
      <div class="editor__meta">
        <div class="field">
          <label for="editor-name">Workflow name</label>
          <input id="editor-name" v-model="editorName" type="text" maxlength="120" placeholder="Tidy the monthly exports" />
        </div>
        <div class="field">
          <label for="editor-category">Category</label>
          <select id="editor-category" v-model="editorCategory">
            <option v-for="category in categories" :key="category" :value="category">
              {{ titleCase(category) }}
            </option>
          </select>
        </div>
        <div class="field editor__description">
          <label for="editor-description">Description</label>
          <textarea id="editor-description" v-model="editorDescription" rows="2" maxlength="2000" />
        </div>
      </div>

      <div v-if="editorNotice" class="notice notice--accent editor__notice">
        <Icon name="brain" :size="15" class="notice__icon" />
        <span>{{ editorNotice }}</span>
      </div>

      <div class="field">
        <label for="editor-json">Definition JSON</label>
        <textarea
          id="editor-json"
          v-model="editorJson"
          class="editor__json"
          rows="18"
          spellcheck="false"
        />
        <p class="field__hint">
          Only the fixed action types are accepted. Invalid workflows save as drafts and cannot run.
        </p>
      </div>

      <div v-if="editorValidation" class="editor__validation">
        <div class="row row--between">
          <StatusPill
            :tone="editorValidation.valid ? 'success' : 'danger'"
            :label="editorValidation.valid ? 'Definition valid' : `${editorValidation.errors?.length ?? 0} validation ${plural(editorValidation.errors?.length ?? 0, 'error')}`"
          />
          <span class="faint tiny">
            {{ editorValidation.requiredPermissions?.length ?? 0 }}
            {{ plural(editorValidation.requiredPermissions?.length ?? 0, 'permission') }}
          </span>
        </div>
        <ul v-if="editorValidation.errors?.length" class="issue-list issue-list--danger">
          <li v-for="issue in editorValidation.errors" :key="issue">{{ issue }}</li>
        </ul>
        <ul v-if="editorValidation.warnings?.length" class="issue-list issue-list--warn">
          <li v-for="warning in editorValidation.warnings" :key="warning">{{ warning }}</li>
        </ul>
      </div>

      <p v-if="editorError" class="replay-error" role="alert">{{ editorError }}</p>
    </div>
    <template #footer>
      <button class="btn btn--ghost" type="button" :disabled="editingSaving" @click="editorOpen = false">Cancel</button>
      <button class="btn" type="button" :disabled="editingValidating || editingSaving" @click="validateEditor">
        <span v-if="editingValidating" class="spinner" />
        <Icon v-else name="check" :size="14" />
        Validate
      </button>
      <button class="btn btn--primary" type="button" :disabled="editingSaving" @click="saveEditor">
        <span v-if="editingSaving" class="spinner" />
        <Icon v-else name="archive" :size="14" />
        {{ editorMode === 'create' ? 'Save workflow' : 'Save new version' }}
      </button>
    </template>
  </Modal>

  <ConfirmDialog
    :open="pendingAction !== null"
    :title="confirmCopy.title"
    :description="confirmCopy.description"
    :confirm-label="confirmCopy.label"
    :tone="confirmCopy.tone"
    :busy="actionBusy"
    @close="pendingAction = null"
    @confirm="confirmAction"
  >
    <div class="notice" :class="confirmCopy.tone === 'danger' ? 'notice--warning' : 'notice--accent'">
      <Icon :name="confirmCopy.tone === 'danger' ? 'alert' : 'shield'" :size="15" class="notice__icon" />
      <span>{{ confirmCopy.note }}</span>
    </div>
  </ConfirmDialog>
</template>

<style scoped>
.replay-error {
  margin: 0 0 1rem;
  padding: 0.65rem 0.85rem;
  border: 1px solid color-mix(in srgb, var(--danger) 30%, var(--line));
  border-left: 2px solid var(--danger);
  border-radius: var(--radius-sm);
  color: var(--danger);
  font-size: 0.78rem;
  line-height: 1.5;
}
.archived-toggle {
  font-size: 0.74rem;
  white-space: nowrap;
}
.workflow-strip {
  display: flex;
  gap: 0.5rem;
  overflow-x: auto;
  padding-bottom: 0.3rem;
  margin-bottom: 1.2rem;
}
.workflow-tab {
  flex: 0 0 250px;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0.65rem 0.75rem;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text);
  text-align: left;
}
.workflow-tab:hover {
  border-color: var(--line-strong);
}
.workflow-tab--active {
  border-color: color-mix(in srgb, var(--accent) 35%, var(--line));
  background: color-mix(in srgb, var(--accent) 6%, var(--surface));
}
.workflow-tab__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}
.workflow-tab__name {
  font-size: 0.78rem;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.workflow-tab__meta {
  font-family: var(--font-mono);
  font-size: 0.58rem;
  color: var(--text-faint);
}
.replay-loading {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.replay-main {
  align-items: start;
}
.workflow-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.25rem 1.35rem 1rem;
}
.workflow-head__copy {
  min-width: 0;
}
.workflow-head h2 {
  margin: 0.55rem 0 0;
  font-family: var(--font-display);
  font-size: clamp(1.5rem, 2.4vw, 1.9rem);
  font-weight: 600;
  letter-spacing: -0.04em;
  line-height: 1.1;
}
.workflow-head__desc {
  margin: 0.55rem 0 0;
  max-width: 72ch;
  color: var(--text-muted);
  font-size: 0.82rem;
  line-height: 1.6;
}
.workflow-head__meta {
  margin: 0.6rem 0 0;
  color: var(--text-faint);
  font-size: 0.6rem;
  overflow-wrap: anywhere;
}
.workflow-head__actions {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.workflow-state {
  display: flex;
  align-items: flex-start;
  gap: 0.45rem;
  margin: 0 1.35rem 1rem;
  padding: 0.6rem 0.8rem;
  border: 1px solid var(--line);
  border-left: 2px solid var(--line-strong);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--surface-2) 55%, transparent);
  color: var(--text-muted);
  font-size: 0.76rem;
  line-height: 1.5;
}
.workflow-state .icon {
  color: var(--text-faint);
  margin-top: 0.12rem;
}
.workflow-state--ready {
  border-left-color: var(--ok);
}
.workflow-state--ready .icon {
  color: var(--ok);
}
.validation-block {
  margin: 0 1.35rem 1.1rem;
  padding: 0.9rem 1rem;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--surface-2) 40%, transparent);
}
.validation-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
}
.validation-head h3 {
  margin: 0;
  font-size: 0.8rem;
  font-weight: 600;
}
.issue-list {
  margin: 0.65rem 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.issue-list li {
  display: flex;
  align-items: flex-start;
  gap: 0.35rem;
  font-size: 0.74rem;
  line-height: 1.5;
  color: var(--text-muted);
}
.issue-list .icon {
  margin-top: 0.1rem;
  flex: none;
}
.issue-list--danger li,
.issue-list--danger .icon {
  color: var(--danger);
}
.issue-list--warn li,
.issue-list--warn .icon {
  color: var(--warn);
}
.permission-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
  margin-top: 0.7rem;
}
.risk-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.7rem;
  padding-top: 0.65rem;
  border-top: 1px solid var(--line);
}
.risk-row .eyebrow {
  margin-right: 0.2rem;
}
.risk-chip {
  font-family: var(--font-mono);
  font-size: 0.6rem;
  color: var(--text-faint);
}
.panel__head--inner {
  border-top: 1px solid var(--line);
}
.panel__hint {
  color: var(--text-faint);
  font-family: var(--font-sans);
  font-size: 0.72rem;
}
.step-params {
  display: block;
  max-width: 260px;
  font-size: 0.62rem;
  line-height: 1.45;
  color: var(--text-faint);
  overflow-wrap: anywhere;
}
.version-block {
  border-top: 1px solid var(--line);
  padding: 0.85rem 1.35rem 1.1rem;
}
.version-block summary {
  cursor: pointer;
  color: var(--text-muted);
  font-family: var(--font-sans);
  font-size: 0.72rem;
  font-weight: 650;
  letter-spacing: 0;
  text-transform: none;
}
.version-block summary:hover {
  color: var(--text);
}
.version-list {
  display: flex;
  flex-direction: column;
  margin-top: 0.8rem;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.version-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.6rem 0.8rem;
  border-bottom: 1px solid var(--line);
  background: color-mix(in srgb, var(--surface-2) 40%, transparent);
}
.version-row:last-child {
  border-bottom: 0;
}
.version-row__number {
  color: var(--accent);
  font-size: 0.68rem;
}
.version-row__copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}
.version-row__copy strong {
  font-size: 0.74rem;
  font-weight: 550;
}
.version-row__copy span {
  margin-top: 0.1rem;
  font-family: var(--font-mono);
  font-size: 0.58rem;
  color: var(--text-faint);
}
.replay-rule__body {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
}
.replay-rule__body > .icon {
  color: var(--accent);
  margin-top: 0.15rem;
}
.replay-rule h3 {
  margin: 0;
  font-size: 0.84rem;
  font-weight: 600;
}
.replay-rule p {
  margin: 0.35rem 0 0;
  color: var(--text-muted);
  font-size: 0.77rem;
  line-height: 1.6;
  max-width: 78ch;
}
.run-panel__check {
  align-items: flex-start;
  line-height: 1.45;
  font-size: 0.78rem;
}
.run-actions {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}
.run-panel__last {
  margin: 0;
}
.panel__link {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  border: 0;
  background: none;
  padding: 0;
  font-family: var(--font-sans);
  font-size: 0.74rem;
  font-weight: 650;
  color: var(--accent);
  cursor: pointer;
}
.panel__link:hover {
  color: var(--accent-strong);
}
.draft-result {
  display: flex;
  align-items: flex-start;
  gap: 0.4rem;
  margin: 0;
  padding: 0.55rem 0.65rem;
  border: 1px solid color-mix(in srgb, var(--accent) 22%, var(--line));
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--accent) 5%, transparent);
  color: var(--text-muted);
  font-size: 0.73rem;
  line-height: 1.5;
}
.draft-result .icon {
  color: var(--accent);
  margin-top: 0.08rem;
}
.workflow-rules {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}
.workflow-rules p {
  display: flex;
  align-items: flex-start;
  gap: 0.4rem;
  margin: 0;
  font-size: 0.73rem;
  line-height: 1.45;
  color: var(--text-muted);
}
.workflow-rules .icon {
  margin-top: 0.06rem;
  color: var(--text-faint);
  flex: none;
}
.editor {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.editor__meta {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(150px, 0.7fr);
  gap: 0.8rem;
}
.editor__description {
  grid-column: 1 / -1;
}
.editor__notice {
  margin: 0;
}
.editor__json {
  min-height: 19rem;
  font-family: var(--font-mono);
  font-size: 0.73rem;
  line-height: 1.55;
  white-space: pre;
  tab-size: 2;
}
.editor__validation {
  padding: 0.75rem 0.8rem;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--surface-2) 45%, transparent);
}

@media (max-width: 1100px) {
  .workflow-head {
    flex-direction: column;
  }
  .workflow-head__actions {
    justify-content: flex-start;
  }
}
@media (max-width: 720px) {
  .editor__meta {
    grid-template-columns: 1fr;
  }
  .editor__description {
    grid-column: auto;
  }
  .workflow-tab {
    flex-basis: 220px;
  }
  .validation-block,
  .workflow-state {
    margin-left: 1rem;
    margin-right: 1rem;
  }
}
</style>
