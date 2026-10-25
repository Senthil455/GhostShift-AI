<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, downloadJson, hasApiToken, setApiToken } from '@/api/client'
import type {
  AiTestResult,
  ApprovalPolicy,
  AppSettings,
  RegisteredDirectory,
  TimeSavingsSettings,
} from '@/api/types'
import { errorDetails, errorMessage } from '@/lib/errors'
import { CATEGORIES, categoryDefaultFolder, humanDate, humanStatus, bytes } from '@/lib/format'
import { useSessionStore } from '@/stores/session'
import { useToastStore } from '@/stores/toast'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import DirectoryPicker from '@/components/DirectoryPicker.vue'
import Icon from '@/components/Icon.vue'
import Modal from '@/components/Modal.vue'
import PageHeader from '@/components/PageHeader.vue'

const session = useSessionStore()
const toast = useToastStore()
const router = useRouter()

const error = ref('')
const busy = ref('')
const loading = ref(false)

const modelForm = reactive({
  endpoint: 'http://localhost:11434',
  model: 'gemma2:2b',
  timeoutSeconds: 120,
  maxInputChars: 12000,
})
const testResult = ref<AiTestResult | null>(null)

const approvalForm = reactive<ApprovalPolicy>({
  fileMovesRequireApproval: true,
  workflowMovesRequireApproval: true,
  logAnalysisRequiresApproval: false,
})

const savingsForm = reactive<TimeSavingsSettings>({
  fileReviewMinutesPerFile: 1,
  logAnalysisMinutes: 15,
  workflowReviewMinutes: 5,
})

const categoryForm = reactive<Record<string, string>>({})
const newDirectoryId = ref<number | null>(null)

const removeTarget = ref<RegisteredDirectory | null>(null)
const resetOpen = ref(false)
const resetScope = ref<'ACTIVITY' | 'RUNS' | 'REPORTS' | 'PLANS' | 'ALL'>('ACTIVITY')
const resetConfirmation = ref('')

const tokenInput = ref('')
const tokenHeld = ref(hasApiToken())
const showToken = ref(false)

const settings = computed<AppSettings | null>(() => session.settings)
const directories = computed(() => session.directories.filter((directory) => directory.active))
const ai = computed(() => session.ai)

const modelDirty = computed(() => {
  const current = settings.value?.model
  if (!current) return false
  return (
    modelForm.endpoint !== current.endpoint ||
    modelForm.model !== current.model ||
    modelForm.timeoutSeconds !== current.timeoutSeconds ||
    modelForm.maxInputChars !== current.maxInputChars
  )
})

const approvalsDirty = computed(() => {
  const current = settings.value?.approvals
  if (!current) return false
  return (
    approvalForm.fileMovesRequireApproval !== current.fileMovesRequireApproval ||
    approvalForm.workflowMovesRequireApproval !== current.workflowMovesRequireApproval ||
    approvalForm.logAnalysisRequiresApproval !== current.logAnalysisRequiresApproval
  )
})

const savingsDirty = computed(() => {
  const current = settings.value?.timeSavings
  if (!current) return false
  return (
    savingsForm.fileReviewMinutesPerFile !== current.fileReviewMinutesPerFile ||
    savingsForm.logAnalysisMinutes !== current.logAnalysisMinutes ||
    savingsForm.workflowReviewMinutes !== current.workflowReviewMinutes
  )
})

const categoriesDirty = computed(() => {
  const current = settings.value?.categoryFolders
  if (!current) return false
  return CATEGORIES.some((category) => (categoryForm[category.value] ?? '') !== (current[category.value] ?? ''))
})

const resetReady = computed(() => resetConfirmation.value.trim() === 'DELETE')
const resetScopeDescription = computed(() => {
  switch (resetScope.value) {
    case 'ACTIVITY':
      return 'Clears the activity feed only. Plans, runs, reports, workflows, and folders stay.'
    case 'RUNS':
      return 'Clears workflow runs, step executions, run events, and approval records. Active runs must be cancelled first.'
    case 'REPORTS':
      return 'Clears generated report records and their Markdown files inside the GhostShift data folder.'
    case 'PLANS':
      return 'Clears organization plans and their recorded file operations. Files already moved are not restored.'
    default:
      return 'Clears activity, runs, plans, reports, workflows, recordings, and learned category corrections. Registered folders remain. User files are never touched.'
  }
})

function syncForms(source: AppSettings | null) {
  if (!source) return
  modelForm.endpoint = source.model.endpoint
  modelForm.model = source.model.model
  modelForm.timeoutSeconds = source.model.timeoutSeconds
  modelForm.maxInputChars = source.model.maxInputChars
  Object.assign(approvalForm, source.approvals)
  Object.assign(savingsForm, source.timeSavings)
  for (const category of CATEGORIES) {
    categoryForm[category.value] = source.categoryFolders[category.value] ?? categoryDefaultFolder(category.value)
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [loaded, system] = await Promise.all([
      api.settings(),
      api.systemInfo().catch(() => null),
      session.refreshDirectories().catch(() => undefined),
    ])
    session.settings = loaded
    if (system) session.system = system
    syncForms(loaded)
  } catch (cause) {
    error.value = withDetails(cause, 'Settings could not be loaded.')
  } finally {
    loading.value = false
  }
}

async function saveModel() {
  busy.value = 'model'
  error.value = ''
  try {
    const saved = await session.saveSettings({
      model: {
        endpoint: modelForm.endpoint.trim(),
        model: modelForm.model.trim(),
        timeoutSeconds: Number(modelForm.timeoutSeconds),
        maxInputChars: Number(modelForm.maxInputChars),
      },
    })
    syncForms(saved)
    toast.success('Model settings saved', 'The endpoint is checked the next time GhostBrain is used.')
    await session.refreshAi(true).catch(() => undefined)
  } catch (cause) {
    error.value = withDetails(cause, 'The model settings could not be saved.')
  } finally {
    busy.value = ''
  }
}

async function testModel() {
  busy.value = 'test'
  error.value = ''
  testResult.value = null
  try {
    const result = await api.testModel({
      endpoint: modelForm.endpoint.trim(),
      model: modelForm.model.trim(),
    })
    testResult.value = result
    if (result.ok) {
      toast.success('Local model answered', `${result.model} · ${result.latencyMs} ms`)
    } else {
      toast.warning('Model test did not pass', result.message)
    }
    await session.refreshAi(true).catch(() => undefined)
  } catch (cause) {
    error.value = withDetails(cause, 'The model connection test failed.')
  } finally {
    busy.value = ''
  }
}

async function saveApprovals() {
  busy.value = 'approvals'
  error.value = ''
  try {
    const saved = await session.saveSettings({ approvals: { ...approvalForm } })
    syncForms(saved)
    toast.success('Approval policy saved', 'New operations use the updated gates immediately.')
  } catch (cause) {
    error.value = withDetails(cause, 'The approval policy could not be saved.')
  } finally {
    busy.value = ''
  }
}

async function saveSavings() {
  busy.value = 'savings'
  error.value = ''
  try {
    const saved = await session.saveSettings({ timeSavings: { ...savingsForm } })
    syncForms(saved)
    await session.refreshDashboard().catch(() => undefined)
    toast.success('Time assumptions saved', 'Estimates will use these values from now on.')
  } catch (cause) {
    error.value = withDetails(cause, 'The time assumptions could not be saved.')
  } finally {
    busy.value = ''
  }
}

async function saveCategories() {
  busy.value = 'categories'
  error.value = ''
  try {
    const saved = await session.saveSettings({ categoryFolders: { ...categoryForm } })
    syncForms(saved)
    toast.success('Category folders saved', 'New plans place files under these folder names.')
  } catch (cause) {
    error.value = withDetails(cause, 'The category folders could not be saved.')
  } finally {
    busy.value = ''
  }
}

function resetCategoryNames() {
  for (const category of CATEGORIES) {
    categoryForm[category.value] = categoryDefaultFolder(category.value)
  }
  toast.info('Defaults restored in the form', 'Save the category folders to apply them.')
}

async function setTheme(theme: 'dark' | 'light') {
  await session.setTheme(theme)
  toast.success(theme === 'dark' ? 'Dark theme active' : 'Light theme active')
}

async function removeDirectory() {
  if (!removeTarget.value) return
  busy.value = 'directory'
  error.value = ''
  try {
    await api.removeDirectory(removeTarget.value.id)
    const name = removeTarget.value.displayName
    removeTarget.value = null
    newDirectoryId.value = null
    await session.refreshDirectories()
    toast.success('Folder removed from GhostShift', `${name} · the folder and its files were not deleted.`)
  } catch (cause) {
    error.value = withDetails(cause, 'That folder registration could not be removed.')
  } finally {
    busy.value = ''
  }
}

function folderCreated() {
  void session.refreshDirectories().catch(() => undefined)
}

function applyToken() {
  const value = tokenInput.value.trim()
  setApiToken(value)
  tokenHeld.value = hasApiToken()
  tokenInput.value = ''
  if (value) {
    toast.success('Token held in memory', 'It will be sent with API requests until this page reloads.')
  } else {
    toast.info('Token cleared', 'Requests no longer include an Authorization header.')
  }
}

function clearToken() {
  setApiToken('')
  tokenInput.value = ''
  tokenHeld.value = false
  toast.info('Token cleared', 'Requests no longer include an Authorization header.')
}

async function exportData() {
  busy.value = 'export'
  error.value = ''
  try {
    const data = await api.exportData()
    const stamp = new Date().toISOString().slice(0, 10)
    await downloadJson(data, `ghostshift-export-${stamp}.json`)
    toast.success('Local export prepared', 'Metadata only. User file contents are not included.')
  } catch (cause) {
    error.value = withDetails(cause, 'The local data export could not be created.')
  } finally {
    busy.value = ''
  }
}

async function seedDemo() {
  busy.value = 'demo'
  error.value = ''
  try {
    const result = await api.seedDemo()
    await Promise.all([
      session.refreshDirectories().catch(() => undefined),
      session.refreshDashboard().catch(() => undefined),
      session.refreshApprovals().catch(() => undefined),
    ])
    toast.success('Demo workspace ready', result.message)
  } catch (cause) {
    error.value = withDetails(cause, 'The demo workspace could not be created.')
  } finally {
    busy.value = ''
  }
}

function openReset() {
  resetScope.value = 'ACTIVITY'
  resetConfirmation.value = ''
  resetOpen.value = true
}

async function resetData() {
  if (!resetReady.value) return
  busy.value = 'reset'
  error.value = ''
  try {
    const result = await api.resetData(resetScope.value, resetConfirmation.value.trim())
    const cleared = Object.entries(result)
      .filter(([key]) => key !== 'notice' && key !== 'scope')
      .map(([key, value]) => `${key}: ${String(value)}`)
      .join(' · ')
    resetOpen.value = false
    resetConfirmation.value = ''
    await Promise.all([
      session.refreshSettings().catch(() => undefined),
      session.refreshDirectories().catch(() => undefined),
      session.refreshDashboard().catch(() => undefined),
      session.refreshApprovals().catch(() => undefined),
    ])
    syncForms(session.settings)
    toast.success('Local data cleared', cleared || 'GhostShift records were cleared. User files were not touched.')
  } catch (cause) {
    error.value = withDetails(cause, 'The local data could not be cleared.')
  } finally {
    busy.value = ''
  }
}

function replayWelcome() {
  void router.push({ name: 'welcome', query: { replay: '1' } })
}

function withDetails(cause: unknown, fallback: string): string {
  const details = errorDetails(cause)
  const message = errorMessage(cause, fallback)
  return details.length ? `${message} ${details.join(' ')}` : message
}

function statusTone() {
  if (!ai.value?.reachable) return 'offline'
  return ai.value.modelAvailable ? 'ready' : 'missing'
}

function statusLabel() {
  if (!ai.value) return 'Unknown'
  if (!ai.value.reachable) return 'Ollama offline'
  if (!ai.value.modelAvailable) return 'Model not installed'
  return 'Ready'
}

onMounted(load)
</script>

<template>
  <PageHeader
    eyebrow="System"
    title="Make the workshop yours."
    description="Everything here is stored locally. GhostShift never uploads settings, and secrets are held in memory rather than written to the database."
  >
    <template #actions>
      <button class="btn btn--ghost btn--sm" type="button" :disabled="loading" @click="load">
        <span v-if="loading" class="spinner" />
        <Icon v-else name="refresh" :size="15" />
        Refresh
      </button>
      <button class="btn btn--sm" type="button" @click="replayWelcome">
        <Icon name="spark" :size="14" />
        Replay welcome
      </button>
    </template>
  </PageHeader>

  <p v-if="error" class="settings-error" role="alert">{{ error }}</p>

  <div class="settings-grid">
    <div class="stack stack--lg">
      <section class="panel" aria-labelledby="model-heading">
        <header class="panel__head">
          <div>
            <h2 id="model-heading">GhostBrain model</h2>
            <p class="panel__sub">Local inference through Ollama. No hosted provider is called.</p>
          </div>
          <span class="model-state" :class="`model-state--${statusTone()}`">
            <span class="model-state__dot" />
            {{ statusLabel() }}
          </span>
        </header>
        <div class="panel__body stack">
          <div class="notice notice--accent">
            <Icon name="brain" :size="16" class="notice__icon" />
            <span>
              GhostShift asks the model for suggestions only. Application code validates every classification, workflow
              step, and log interpretation before anything is recorded or executed.
            </span>
          </div>

          <form class="form-grid" @submit.prevent="saveModel">
            <div class="field field--wide">
              <label for="model-endpoint">Ollama endpoint</label>
              <input
                id="model-endpoint"
                v-model="modelForm.endpoint"
                type="url"
                autocomplete="off"
                spellcheck="false"
                placeholder="http://localhost:11434"
              />
              <p class="field__hint">Only http and https endpoints are accepted. The default stays on this machine.</p>
            </div>
            <div class="field">
              <label for="model-name">Model</label>
              <input
                id="model-name"
                v-model="modelForm.model"
                type="text"
                autocomplete="off"
                spellcheck="false"
                placeholder="gemma2:2b"
              />
              <p class="field__hint">Use a model already installed in Ollama, for example from <code>ollama list</code>.</p>
            </div>
            <div class="field">
              <label for="model-timeout">Inference timeout (seconds)</label>
              <input id="model-timeout" v-model.number="modelForm.timeoutSeconds" type="number" min="5" max="600" />
            </div>
            <div class="field">
              <label for="model-input">Maximum input characters</label>
              <input id="model-input" v-model.number="modelForm.maxInputChars" type="number" min="500" max="200000" step="500" />
              <p class="field__hint">Bounds prompt size for log evidence and workflow descriptions.</p>
            </div>
            <div class="field">
              <span class="field__label">Last known health</span>
              <p class="model-health mono">
                {{ ai?.checkedAt ? `${humanStatus(ai.status)} · ${humanDate(ai.checkedAt)}` : 'Not checked yet' }}
                <template v-if="ai?.latencyMs != null"> · {{ ai.latencyMs }} ms</template>
              </p>
            </div>
          </form>

          <div v-if="testResult" class="model-result" :class="{ 'model-result--ok': testResult.ok }">
            <Icon :name="testResult.ok ? 'check' : 'alert'" :size="15" />
            <div>
              <strong>{{ testResult.ok ? 'Connection succeeded' : 'Connection failed' }}</strong>
              <p>{{ testResult.message }}</p>
              <p v-if="testResult.sample" class="mono model-result__sample">{{ testResult.sample }}</p>
            </div>
          </div>

          <div v-if="ai?.models?.length" class="model-installed">
            <span class="field__label">Models reported by Ollama</span>
            <div class="row model-chips">
              <span
                v-for="model in ai.models"
                :key="model.name"
                class="chip"
                :class="{ 'chip--accent': model.name === settings?.model.model }"
              >
                {{ model.name }}
                <template v-if="model.parameterSize"> · {{ model.parameterSize }}</template>
              </span>
            </div>
          </div>

          <div class="form-actions">
            <button class="btn btn--ghost" type="button" :disabled="busy !== ''" @click="testModel">
              <span v-if="busy === 'test'" class="spinner" />
              <Icon v-else name="pulse" :size="15" />
              Test current values
            </button>
            <button class="btn btn--primary" type="button" :disabled="busy !== '' || !modelDirty" @click="saveModel">
              <span v-if="busy === 'model'" class="spinner" />
              <Icon v-else name="check" :size="15" />
              Save model settings
            </button>
          </div>
          <p class="settings-note">
            Testing uses the values in this form without saving them. A real request is sent to the endpoint you enter.
          </p>
        </div>
      </section>

      <section class="panel" aria-labelledby="folders-heading">
        <header class="panel__head">
          <div>
            <h2 id="folders-heading">Registered folders</h2>
            <p class="panel__sub">The only places GhostShift may read or move files.</p>
          </div>
          <span class="chip">{{ directories.length }} active</span>
        </header>
        <div class="panel__body stack">
          <DirectoryPicker
            v-model="newDirectoryId"
            label="Register a folder"
            hint="Choose an absolute path. Symlinks that escape the folder are rejected, and removing a registration never deletes files."
            :organize-default="true"
            :show-select="false"
            @created="folderCreated"
          />

          <div v-if="directories.length" class="folder-list">
            <article v-for="directory in directories" :key="directory.id" class="folder-row">
              <span class="folder-row__icon"><Icon name="folder" :size="17" /></span>
              <div class="folder-row__copy">
                <div class="folder-row__title">
                  <strong>{{ directory.displayName }}</strong>
                  <span class="chip">{{ humanStatus(directory.directoryType) }}</span>
                  <span class="chip" :class="{ 'chip--accent': directory.allowOrganize }">
                    {{ directory.allowOrganize ? 'Organize allowed' : 'Read only' }}
                  </span>
                </div>
                <p class="folder-row__path mono">{{ directory.path }}</p>
                <p class="folder-row__meta">
                  Registered {{ humanDate(directory.createdAt) }}
                  <template v-if="directory.lastScanAt"> · last accessed {{ humanDate(directory.lastScanAt) }}</template>
                </p>
              </div>
              <button
                class="btn btn--sm btn--ghost folder-row__remove"
                type="button"
                @click="removeTarget = directory"
              >
                <Icon name="trash" :size="14" />
                Remove
              </button>
            </article>
          </div>
          <div v-else class="settings-empty">
            <Icon name="folder" :size="20" />
            <p>No folders registered. GhostShift cannot inspect anything until you add one.</p>
          </div>
        </div>
      </section>

      <section class="panel" aria-labelledby="approvals-heading">
        <header class="panel__head">
          <div>
            <h2 id="approvals-heading">Approval policy</h2>
            <p class="panel__sub">Gates are enforced by the backend, not just hidden in the interface.</p>
          </div>
          <span v-if="approvalsDirty" class="chip chip--accent">Unsaved</span>
        </header>
        <div class="panel__body stack">
          <label class="policy-row">
            <input v-model="approvalForm.fileMovesRequireApproval" type="checkbox" />
            <span>
              <strong>Pause before executing an organization plan</strong>
              <small>
                A GhostSort plan stays in review until you approve files. Turning this off auto-approves every proposed
                file when you start execution; files still cannot be overwritten or deleted.
              </small>
            </span>
          </label>
          <label class="policy-row">
            <input v-model="approvalForm.workflowMovesRequireApproval" type="checkbox" />
            <span>
              <strong>Pause before a workflow moves files</strong>
              <small>
                Adds a human gate before the MOVE_FILE action in live workflow runs. Explicit
                <code>REQUEST_USER_APPROVAL</code> steps in a workflow are always honored, even with this off.
              </small>
            </span>
          </label>
          <label class="policy-row">
            <input v-model="approvalForm.logAnalysisRequiresApproval" type="checkbox" />
            <span>
              <strong>Pause before workflow log analysis</strong>
              <small>
                Requests approval before a live <code>ANALYZE_LOG</code> step sends evidence to the local model. Manual
                GhostDev analysis and dry runs are unaffected.
              </small>
            </span>
          </label>
          <div class="form-actions">
            <button class="btn btn--primary" type="button" :disabled="busy !== '' || !approvalsDirty" @click="saveApprovals">
              <span v-if="busy === 'approvals'" class="spinner" />
              <Icon v-else name="shield" :size="15" />
              Save approval policy
            </button>
          </div>
        </div>
      </section>

      <section class="panel" aria-labelledby="estimates-heading">
        <header class="panel__head">
          <div>
            <h2 id="estimates-heading">Time assumptions</h2>
            <p class="panel__sub">Inputs for an estimate, not a measurement of your work.</p>
          </div>
          <span v-if="savingsDirty" class="chip chip--accent">Unsaved</span>
        </header>
        <div class="panel__body stack">
          <div class="notice">
            <Icon name="clock" :size="15" class="notice__icon" />
            <span>
              GhostShift reports an assumption-based estimate only. It never claims a verified productivity study.
            </span>
          </div>
          <form class="form-grid" @submit.prevent="saveSavings">
            <div class="field">
              <label for="savings-files">Minutes to review one file manually</label>
              <input id="savings-files" v-model.number="savingsForm.fileReviewMinutesPerFile" type="number" min="0" max="600" step="0.5" />
            </div>
            <div class="field">
              <label for="savings-log">Minutes to analyze one log manually</label>
              <input id="savings-log" v-model.number="savingsForm.logAnalysisMinutes" type="number" min="0" max="1440" />
            </div>
            <div class="field">
              <label for="savings-workflow">Minutes to review one workflow manually</label>
              <input id="savings-workflow" v-model.number="savingsForm.workflowReviewMinutes" type="number" min="0" max="1440" />
            </div>
          </form>
          <div class="form-actions">
            <button class="btn btn--primary" type="button" :disabled="busy !== '' || !savingsDirty" @click="saveSavings">
              <span v-if="busy === 'savings'" class="spinner" />
              <Icon v-else name="check" :size="15" />
              Save assumptions
            </button>
          </div>
        </div>
      </section>

      <section class="panel" aria-labelledby="categories-heading">
        <header class="panel__head">
          <div>
            <h2 id="categories-heading">Category folder names</h2>
            <p class="panel__sub">Files are placed under GhostShift Organizer / these names.</p>
          </div>
          <span v-if="categoriesDirty" class="chip chip--accent">Unsaved</span>
        </header>
        <div class="panel__body stack">
          <p class="settings-note">
            Names are sanitized by the backend. Path separators and reserved characters are replaced, and empty values
            fall back to the default. Changing a name only affects plans created afterward.
          </p>
          <form class="category-grid" @submit.prevent="saveCategories">
            <div v-for="category in CATEGORIES" :key="category.value" class="field">
              <label :for="`category-${category.value}`">{{ category.label }}</label>
              <input
                :id="`category-${category.value}`"
                v-model="categoryForm[category.value]"
                type="text"
                maxlength="80"
                autocomplete="off"
                spellcheck="false"
              />
            </div>
          </form>
          <div class="form-actions">
            <button class="btn btn--ghost" type="button" @click="resetCategoryNames">
              <Icon name="undo" :size="14" />
              Restore defaults
            </button>
            <button class="btn btn--primary" type="button" :disabled="busy !== '' || !categoriesDirty" @click="saveCategories">
              <span v-if="busy === 'categories'" class="spinner" />
              <Icon v-else name="check" :size="15" />
              Save category folders
            </button>
          </div>
        </div>
      </section>

      <section class="panel" aria-labelledby="data-heading">
        <header class="panel__head">
          <div>
            <h2 id="data-heading">Local data</h2>
            <p class="panel__sub">Export, seed, or clear GhostShift's own records.</p>
          </div>
        </header>
        <div class="panel__body stack">
          <div class="data-actions">
            <div class="data-action">
              <span class="data-action__icon"><Icon name="download" :size="18" /></span>
              <div>
                <strong>Export metadata</strong>
                <p>Settings, folders, plans, runs, approvals, and activity as JSON. File contents are not included.</p>
              </div>
              <button class="btn btn--sm" type="button" :disabled="busy !== ''" @click="exportData">
                <span v-if="busy === 'export'" class="spinner" />
                <Icon v-else name="download" :size="14" />
                Export
              </button>
            </div>
            <div class="data-action">
              <span class="data-action__icon"><Icon name="spark" :size="18" /></span>
              <div>
                <strong>Demo workspace</strong>
                <p>Creates synthetic files and example workflows inside GhostShift's data folder. Never overwrites existing files.</p>
              </div>
              <button class="btn btn--sm" type="button" :disabled="busy !== ''" @click="seedDemo">
                <span v-if="busy === 'demo'" class="spinner" />
                <Icon v-else name="spark" :size="14" />
                Create demo
              </button>
            </div>
          </div>

          <div class="danger-zone">
            <div>
              <strong>Clear local records</strong>
              <p>
                Removes selected records from the GhostShift database. It never deletes, moves, or overwrites your files.
                This cannot be undone.
              </p>
            </div>
            <button class="btn btn--sm btn--danger" type="button" @click="openReset">
              <Icon name="trash" :size="14" />
              Clear records
            </button>
          </div>
        </div>
      </section>
    </div>

    <aside class="stack settings-aside">
      <section class="panel" aria-labelledby="appearance-heading">
        <header class="panel__head">
          <h2 id="appearance-heading">Appearance</h2>
        </header>
        <div class="panel__body stack">
          <div class="theme-picker" role="group" aria-label="Color theme">
            <button
              class="theme-option"
              :class="{ 'theme-option--active': session.theme !== 'light' }"
              type="button"
              :aria-pressed="session.theme !== 'light'"
              @click="setTheme('dark')"
            >
              <span class="theme-option__swatch theme-option__swatch--dark"><i /><i /><b /></span>
              <span>Dark</span>
              <small>Ink and moss</small>
            </button>
            <button
              class="theme-option"
              :class="{ 'theme-option--active': session.theme === 'light' }"
              type="button"
              :aria-pressed="session.theme === 'light'"
              @click="setTheme('light')"
            >
              <span class="theme-option__swatch theme-option__swatch--light"><i /><i /><b /></span>
              <span>Light</span>
              <small>Paper and moss</small>
            </button>
          </div>
          <p class="settings-note">Theme is a local preference and is remembered in this browser.</p>
        </div>
      </section>

      <section class="panel" aria-labelledby="token-heading">
        <header class="panel__head">
          <h2 id="token-heading">Local access token</h2>
          <span v-if="tokenHeld" class="chip chip--accent">Held</span>
        </header>
        <div class="panel__body stack">
          <div class="field">
            <label for="api-token">Bearer token</label>
            <div class="token-row">
              <input
                id="api-token"
                v-model="tokenInput"
                :type="showToken ? 'text' : 'password'"
                autocomplete="off"
                spellcheck="false"
                placeholder="Paste the token configured on the backend"
                @keyup.enter="applyToken"
              />
              <button
                class="icon-btn"
                type="button"
                :aria-label="showToken ? 'Hide token' : 'Show token'"
                @click="showToken = !showToken"
              >
                <Icon name="eye" :size="16" />
              </button>
            </div>
            <p class="field__hint">
              Kept in memory for this tab only. It is never written to localStorage, SQLite, or export files. Reloading
              clears it.
            </p>
          </div>
          <div class="row">
            <button class="btn btn--sm btn--primary" type="button" :disabled="!tokenInput.trim()" @click="applyToken">
              Use this session
            </button>
            <button v-if="tokenHeld" class="btn btn--sm btn--ghost" type="button" @click="clearToken">Clear</button>
          </div>
          <div class="notice" :class="{ 'notice--warning': session.system?.apiTokenEnabled }">
            <Icon name="lock" :size="14" class="notice__icon" />
            <span v-if="session.system?.apiTokenEnabled">
              The backend currently requires a token for API requests. Set <code>GHOSTSHIFT_API_TOKEN</code> there.
            </span>
            <span v-else>
              The backend is not currently requiring a token. Set <code>GHOSTSHIFT_API_TOKEN</code> before exposing the
              service beyond this machine.
            </span>
          </div>
        </div>
      </section>

      <section class="panel" aria-labelledby="system-heading">
        <header class="panel__head">
          <h2 id="system-heading">System facts</h2>
          <span class="chip">{{ session.system?.version ?? '1.0.0' }}</span>
        </header>
        <div class="panel__body">
          <dl class="facts">
            <div>
              <dt>Bind address</dt>
              <dd class="mono">{{ session.system?.bind ?? '127.0.0.1:8080' }}</dd>
            </div>
            <div>
              <dt>Data directory</dt>
              <dd class="mono fact-break">{{ session.system?.dataDir ?? settings?.dataDir ?? '—' }}</dd>
            </div>
            <div>
              <dt>Database</dt>
              <dd class="mono fact-break">{{ session.system?.databasePath ?? settings?.databasePath ?? '—' }}</dd>
            </div>
            <div>
              <dt>Scan limits</dt>
              <dd>{{ session.system?.maxScanFiles ?? '—' }} files · depth {{ session.system?.maxScanDepth ?? '—' }}</dd>
            </div>
            <div>
              <dt>Log read limit</dt>
              <dd>{{ session.system?.maxLogBytes ? bytes(session.system.maxLogBytes) : '—' }}</dd>
            </div>
            <div>
              <dt>API token required</dt>
              <dd>{{ session.system?.apiTokenEnabled ? 'Yes' : 'No' }}</dd>
            </div>
            <div>
              <dt>Model endpoint</dt>
              <dd class="mono fact-break">{{ ai?.endpoint ?? settings?.model.endpoint ?? '—' }}</dd>
            </div>
          </dl>
        </div>
      </section>

      <div class="aside-note">
        <Icon name="shield" :size="15" />
        <p>
          No account, telemetry, or cloud sync. The model runs through your own Ollama endpoint, and every file operation
          is scoped to a registered folder.
        </p>
      </div>
    </aside>
  </div>

  <ConfirmDialog
    :open="removeTarget !== null"
    title="Remove this folder registration?"
    :description="removeTarget?.displayName"
    confirm-label="Remove registration"
    tone="danger"
    :busy="busy === 'directory'"
    @close="removeTarget = null"
    @confirm="removeDirectory"
  >
    <div class="notice notice--warning">
      <Icon name="alert" :size="15" class="notice__icon" />
      <span>
        GhostShift will stop reading this folder. The folder and every file inside it stay exactly where they are. Plans
        and history that refer to it remain as records.
      </span>
    </div>
  </ConfirmDialog>

  <Modal :open="resetOpen" title="Clear local records?" subtitle="This action is irreversible." @close="resetOpen = false">
    <div class="stack">
      <div class="field">
        <label for="reset-scope">What should be cleared?</label>
        <select id="reset-scope" v-model="resetScope">
          <option value="ACTIVITY">Activity feed only</option>
          <option value="RUNS">Workflow runs and approvals</option>
          <option value="REPORTS">Generated reports</option>
          <option value="PLANS">Organization plans</option>
          <option value="ALL">Everything GhostShift recorded</option>
        </select>
        <p class="field__hint">{{ resetScopeDescription }}</p>
      </div>
      <div class="notice notice--danger">
        <Icon name="alert" :size="15" class="notice__icon" />
        <span>
          Your user files are never deleted. Plans and run records removed here cannot be reconstructed, even if files
          were moved earlier.
        </span>
      </div>
      <div class="field">
        <label for="reset-confirm">Type DELETE to confirm</label>
        <input
          id="reset-confirm"
          v-model="resetConfirmation"
          type="text"
          autocomplete="off"
          spellcheck="false"
          placeholder="DELETE"
        />
        <p class="field__hint">The backend requires the exact word. Active runs or plans must be cancelled first.</p>
      </div>
    </div>
    <template #footer>
      <button class="btn btn--ghost" type="button" @click="resetOpen = false">Cancel</button>
      <button class="btn btn--danger" type="button" :disabled="!resetReady || busy === 'reset'" @click="resetData">
        <span v-if="busy === 'reset'" class="spinner" />
        <Icon v-else name="trash" :size="15" />
        Clear {{ resetScope === 'ALL' ? 'all records' : humanStatus(resetScope).toLowerCase() }}
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.settings-error {
  margin: 0 0 1.2rem;
  padding: 0.7rem 0.9rem;
  border: 1px solid color-mix(in srgb, var(--danger) 30%, var(--line));
  border-left: 2px solid var(--danger);
  border-radius: var(--radius-sm);
  color: var(--danger);
  font-size: 0.8rem;
}
.settings-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.75fr) minmax(280px, 1fr);
  gap: 1.4rem;
  align-items: start;
}
.settings-aside {
  position: sticky;
  top: 4.6rem;
}
.panel__sub {
  margin: 0.2rem 0 0;
  color: var(--text-faint);
  font-size: 0.72rem;
  line-height: 1.45;
}
.model-state {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--text-faint);
  font-family: var(--font-sans);
  font-size: 0.7rem;
  font-weight: 650;
  letter-spacing: 0;
  text-transform: none;
  white-space: nowrap;
}
.model-state__dot {
  width: 6px;
  height: 6px;
  border-radius: 0;
  background: currentColor;
}
.model-state--ready {
  color: var(--ok);
}
.model-state--missing {
  color: var(--warn);
}
.model-state--offline {
  color: var(--text-faint);
}
.model-health {
  margin: 0;
  padding: 0.55rem 0;
  color: var(--text-muted);
  font-size: 0.7rem;
}
.model-result {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  padding: 0.75rem 0.85rem;
  border: 1px solid color-mix(in srgb, var(--danger) 28%, var(--line));
  border-left: 2px solid var(--danger);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--danger) 6%, transparent);
  font-size: 0.78rem;
  color: var(--text-muted);
}
.model-result .icon {
  color: var(--danger);
  margin-top: 0.1rem;
}
.model-result--ok {
  border-color: color-mix(in srgb, var(--ok) 28%, var(--line));
  border-left-color: var(--ok);
  background: color-mix(in srgb, var(--ok) 6%, transparent);
}
.model-result--ok .icon {
  color: var(--ok);
}
.model-result strong {
  color: var(--text);
}
.model-result p {
  margin: 0.2rem 0 0;
  line-height: 1.45;
}
.model-result__sample {
  color: var(--text-faint);
  font-size: 0.67rem;
  max-height: 5rem;
  overflow: auto;
  white-space: pre-wrap;
}
.model-installed {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}
.model-chips {
  gap: 0.35rem;
}
.settings-note {
  margin: 0;
  color: var(--text-faint);
  font-size: 0.73rem;
  line-height: 1.55;
}
.folder-list {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  overflow: hidden;
}
.folder-row {
  display: flex;
  align-items: flex-start;
  gap: 0.7rem;
  padding: 0.8rem 0.9rem;
  border-bottom: 1px solid var(--line);
  background: color-mix(in srgb, var(--surface-2) 35%, transparent);
}
.folder-row:last-child {
  border-bottom: 0;
}
.folder-row__icon {
  display: grid;
  place-items: center;
  width: 1.9rem;
  height: 1.9rem;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--accent);
  flex: none;
}
.folder-row__copy {
  min-width: 0;
  flex: 1;
}
.folder-row__title {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex-wrap: wrap;
}
.folder-row__title strong {
  font-size: 0.82rem;
  font-weight: 600;
}
.folder-row__path {
  margin: 0.25rem 0 0;
  color: var(--text-faint);
  font-size: 0.64rem;
  overflow-wrap: anywhere;
}
.folder-row__meta {
  margin: 0.3rem 0 0;
  color: var(--text-faint);
  font-size: 0.66rem;
}
.folder-row__remove {
  flex: none;
}
.settings-empty {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.9rem;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-sm);
  color: var(--text-faint);
  font-size: 0.78rem;
}
.settings-empty p {
  margin: 0;
}
.policy-row {
  display: flex;
  align-items: flex-start;
  gap: 0.7rem;
  padding: 0.75rem 0.85rem;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--surface-2) 35%, transparent);
  cursor: pointer;
}
.policy-row input {
  margin-top: 0.15rem;
  flex: none;
}
.policy-row span {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}
.policy-row strong {
  font-size: 0.82rem;
  font-weight: 600;
}
.policy-row small {
  color: var(--text-muted);
  font-size: 0.73rem;
  line-height: 1.5;
  max-width: 70ch;
}
.policy-row code {
  font-size: 0.9em;
  color: var(--text);
}
.category-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
}
.category-grid input {
  min-height: 2.1rem;
  font-family: var(--font-mono);
  font-size: 0.74rem;
}
.data-actions {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}
.data-action {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.8rem 0.85rem;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}
.data-action__icon {
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--accent);
  flex: none;
}
.data-action div {
  flex: 1;
  min-width: 0;
}
.data-action strong {
  display: block;
  font-size: 0.8rem;
  font-weight: 600;
}
.data-action p {
  margin: 0.15rem 0 0;
  color: var(--text-muted);
  font-size: 0.72rem;
  line-height: 1.45;
}
.danger-zone {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-top: 0.4rem;
  padding: 0.9rem;
  border: 1px solid color-mix(in srgb, var(--danger) 28%, var(--line));
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--danger) 4%, transparent);
}
.danger-zone strong {
  font-size: 0.8rem;
}
.danger-zone p {
  margin: 0.2rem 0 0;
  color: var(--text-muted);
  font-size: 0.72rem;
  line-height: 1.5;
  max-width: 62ch;
}
.danger-zone .btn {
  flex: none;
}
.theme-picker {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.5rem;
}
.theme-option {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.2rem;
  padding: 0.55rem;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text);
  text-align: left;
  transition:
    border-color 140ms ease,
    background 140ms ease;
}
.theme-option:hover {
  border-color: var(--line-strong);
  background: var(--surface-2);
}
.theme-option--active {
  border-color: color-mix(in srgb, var(--accent) 48%, var(--line));
  background: color-mix(in srgb, var(--accent) 7%, transparent);
}
.theme-option > span:not(.theme-option__swatch) {
  font-size: 0.76rem;
  font-weight: 600;
}
.theme-option small {
  color: var(--text-faint);
  font-size: 0.64rem;
}
.theme-option__swatch {
  position: relative;
  display: block;
  width: 100%;
  height: 2.1rem;
  margin-bottom: 0.3rem;
  border: 1px solid var(--line);
  border-radius: 4px;
  overflow: hidden;
}
.theme-option__swatch i {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 38%;
  background: #0c100d;
}
.theme-option__swatch i:first-child {
  left: 0;
}
.theme-option__swatch i:nth-child(2) {
  left: 38%;
  width: 1px;
  background: #2b352c;
}
.theme-option__swatch b {
  position: absolute;
  right: 0.45rem;
  bottom: 0.45rem;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #c6e6a5;
}
.theme-option__swatch--light i:first-child {
  background: #f2efe5;
}
.theme-option__swatch--light i:nth-child(2) {
  background: #ddd7c8;
}
.theme-option__swatch--light b {
  background: #3f6834;
}
.token-row {
  display: flex;
  align-items: center;
  gap: 0.3rem;
}
.token-row input {
  flex: 1;
  min-width: 0;
}
.token-row .icon-btn {
  border-color: var(--line);
  flex: none;
}
.facts {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}
.facts > div {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  padding-bottom: 0.55rem;
  border-bottom: 1px solid color-mix(in srgb, var(--line) 60%, transparent);
}
.facts > div:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}
.facts dt {
  color: var(--text-faint);
  font-family: var(--font-sans);
  font-size: 0.68rem;
  font-weight: 650;
  letter-spacing: 0;
  text-transform: none;
}
.facts dd {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.75rem;
}
.fact-break {
  overflow-wrap: anywhere;
  font-size: 0.64rem !important;
}
.aside-note {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.2rem 0.35rem;
  color: var(--text-faint);
}
.aside-note .icon {
  color: var(--accent);
  margin-top: 0.1rem;
}
.aside-note p {
  margin: 0;
  font-size: 0.7rem;
  line-height: 1.55;
}

@media (max-width: 1100px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }
  .settings-aside {
    position: static;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: start;
  }
  .settings-aside .aside-note {
    grid-column: 1 / -1;
  }
}
@media (max-width: 720px) {
  .settings-aside {
    grid-template-columns: 1fr;
  }
  .category-grid {
    grid-template-columns: 1fr;
  }
  .data-action {
    flex-wrap: wrap;
  }
  .data-action .btn {
    width: 100%;
  }
  .danger-zone {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
