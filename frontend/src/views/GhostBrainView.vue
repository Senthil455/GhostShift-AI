<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/api/client'
import type { AiModelInfo, AiTestResult, ReportSummary } from '@/api/types'
import { errorDetails, errorMessage } from '@/lib/errors'
import { bytes, humanDate, relativeTime } from '@/lib/format'
import { useSessionStore } from '@/stores/session'
import { useToastStore } from '@/stores/toast'
import EmptyState from '@/components/EmptyState.vue'
import Icon from '@/components/Icon.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'

const session = useSessionStore()
const toast = useToastStore()

const endpoint = ref('')
const model = ref('')
const timeoutSeconds = ref(120)
const maxInputChars = ref(60000)
const saving = ref(false)
const testing = ref(false)
const refreshing = ref(false)
const formError = ref('')
const pageError = ref('')
const testResult = ref<AiTestResult | null>(null)
const reports = ref<ReportSummary[]>([])
const reportsLoading = ref(false)

const status = computed(() => session.ai)
const models = computed<AiModelInfo[]>(() => status.value?.models ?? [])
const aiReports = computed(() => reports.value.filter((report) => report.aiUsed).slice(0, 6))
const installModel = computed(() => status.value?.model || model.value || 'gemma2:2b')

const stateLabel = computed(() => {
  if (!status.value) return 'Unknown'
  if (!status.value.reachable) return 'Ollama offline'
  if (!status.value.modelAvailable) return 'Model missing'
  return 'Ready'
})

const stateTone = computed(() => {
  if (!status.value?.reachable) return 'danger'
  return status.value.modelAvailable ? 'success' : 'warning'
})

const stateNote = computed(() => {
  if (!status.value) {
    return 'GhostShift has not checked the local runtime yet. The rest of the workspace keeps working with deterministic rules.'
  }
  if (!status.value.reachable) {
    return 'Ollama is not reachable at the configured endpoint. No remote service is used as a fallback. Start Ollama locally, then check again.'
  }
  if (!status.value.modelAvailable) {
    return status.value.message || 'Ollama answered, but the configured model is not installed. Pull it or choose one of the reported models.'
  }
  return status.value.message || 'The local model answered and is available for classification, log interpretation, and workflow drafts.'
})

const prompts = [
  {
    title: 'Tidy a folder by file type',
    text: 'List the files in a folder, classify them with deterministic rules, preview destinations inside an organizer folder, pause for approval, then move only approved files without overwriting anything.',
    icon: 'sort',
  },
  {
    title: 'Triage a log file',
    text: 'Find the .log files in a folder, read the first one, group repeated error signatures, and save a diagnostic incident report. Do not move or edit any file.',
    icon: 'bug',
  },
  {
    title: 'Group exact duplicates',
    text: 'List files recursively and hash them to group exact duplicates. This is a read-only investigation; never delete a file.',
    icon: 'copy',
  },
]

function syncForm() {
  const settings = session.settings?.model
  if (settings) {
    endpoint.value = settings.endpoint
    model.value = settings.model
    timeoutSeconds.value = settings.timeoutSeconds
    maxInputChars.value = settings.maxInputChars
  } else if (status.value) {
    endpoint.value = status.value.endpoint
    model.value = status.value.model
  }
}

async function refreshStatus(refresh: boolean) {
  refreshing.value = true
  pageError.value = ''
  try {
    await session.refreshAi(refresh)
    syncForm()
  } catch (cause) {
    pageError.value = errorMessage(cause, 'GhostBrain status could not be checked.')
  } finally {
    refreshing.value = false
  }
}

async function loadReports() {
  reportsLoading.value = true
  try {
    reports.value = await api.reports(50)
  } catch {
    // The report list is secondary; a failure here must not hide model controls.
  } finally {
    reportsLoading.value = false
  }
}

async function saveModel() {
  formError.value = ''
  const cleanEndpoint = endpoint.value.trim()
  if (!/^https?:\/\/.+/i.test(cleanEndpoint)) {
    formError.value = 'Enter a complete http:// or https:// endpoint for the local Ollama service.'
    return
  }
  if (!model.value.trim()) {
    formError.value = 'Choose or enter a model name.'
    return
  }
  if (timeoutSeconds.value < 5 || timeoutSeconds.value > 600) {
    formError.value = 'Inference timeout must be between 5 and 600 seconds.'
    return
  }
  if (maxInputChars.value < 500 || maxInputChars.value > 200000) {
    formError.value = 'Maximum input must be between 500 and 200,000 characters.'
    return
  }
  saving.value = true
  try {
    await session.saveSettings({
      model: {
        endpoint: cleanEndpoint,
        model: model.value.trim(),
        timeoutSeconds: timeoutSeconds.value,
        maxInputChars: maxInputChars.value,
      },
    })
    await refreshStatus(true)
    testResult.value = null
    toast.success('GhostBrain configuration saved', `${model.value.trim()} at ${cleanEndpoint}`)
  } catch (cause) {
    const details = errorDetails(cause)
    formError.value = details.length ? `${errorMessage(cause)} ${details.join(' ')}` : errorMessage(cause)
  } finally {
    saving.value = false
  }
}

async function testConnection() {
  formError.value = ''
  testResult.value = null
  testing.value = true
  try {
    const result = await api.testModel({
      endpoint: endpoint.value.trim(),
      model: model.value.trim(),
    })
    testResult.value = result
    if (result.ok) {
      toast.success('Local model answered', `${result.model} · ${result.latencyMs} ms`)
      await session.refreshAi(false)
    } else {
      toast.warning('The test did not pass', result.message)
    }
  } catch (cause) {
    const details = errorDetails(cause)
    formError.value = details.length ? `${errorMessage(cause)} ${details.join(' ')}` : errorMessage(cause)
  } finally {
    testing.value = false
  }
}

function useModel(name: string) {
  model.value = name
  testResult.value = null
  toast.info('Model selected', 'Save the configuration to use it for new AI requests.')
}

async function copy(value: string) {
  try {
    await navigator.clipboard.writeText(value)
    toast.info('Copied', value)
  } catch {
    toast.warning('Copy failed', 'Select the command and copy it manually.')
  }
}

function promptLink(text: string) {
  return { name: 'ghost-replay', query: { prompt: text } }
}

onMounted(async () => {
  syncForm()
  if (!status.value) await refreshStatus(false)
  await loadReports()
})
</script>

<template>
  <PageHeader
    eyebrow="GhostBrain"
    title="A local model with a narrow job."
    description="GhostBrain uses Ollama and an open-weight model on this machine. It can suggest, interpret, and draft. It never authorizes actions, invents validation, or runs anything."
  >
    <template #actions>
      <RouterLink to="/replay" class="btn btn--ghost btn--sm">
        <Icon name="play" :size="14" />
        Open GhostReplay
      </RouterLink>
      <button class="btn btn--primary btn--sm" type="button" :disabled="refreshing" @click="refreshStatus(true)">
        <span v-if="refreshing" class="spinner" />
        <Icon v-else name="refresh" :size="15" />
        Check model
      </button>
    </template>
  </PageHeader>

  <p v-if="pageError" class="brain-error" role="alert">{{ pageError }}</p>

  <section class="brain-hero panel" :class="`brain-hero--${stateTone}`">
    <div class="brain-hero__state">
      <span class="brain-hero__dot" />
      <div>
        <p class="eyebrow">Local runtime</p>
        <h2>{{ stateLabel }}</h2>
        <p class="mono">{{ status?.endpoint || endpoint || 'Endpoint not set' }} · {{ status?.model || model || 'No model' }}</p>
      </div>
      <StatusPill :tone="stateTone" :label="stateLabel" dot />
    </div>
    <p class="brain-hero__note">{{ stateNote }}</p>
    <div class="brain-hero__stats">
      <div>
        <span>Endpoint reachable</span>
        <strong>{{ status?.reachable ? 'Yes' : 'No' }}</strong>
      </div>
      <div>
        <span>Selected model</span>
        <strong>{{ status?.modelAvailable ? 'Installed' : 'Not confirmed' }}</strong>
      </div>
      <div>
        <span>Last latency</span>
        <strong>{{ status?.latencyMs != null ? `${status.latencyMs} ms` : '—' }}</strong>
      </div>
      <div>
        <span>Checked</span>
        <strong class="brain-hero__date">{{ status?.checkedAt ? relativeTime(status.checkedAt) : '—' }}</strong>
      </div>
    </div>
  </section>

  <div class="brain-grid">
    <section class="panel">
      <header class="panel__head">
        <h2>Model connection</h2>
        <span class="panel__hint">Stored locally in SQLite</span>
      </header>
      <form class="panel__body brain-form" @submit.prevent="saveModel">
        <div class="field">
          <label for="brain-endpoint">Ollama endpoint</label>
          <input id="brain-endpoint" v-model="endpoint" type="url" autocomplete="off" placeholder="http://localhost:11434" />
          <p class="field__hint">Only an http(s) URL is accepted. This version supports Ollama as the runtime.</p>
        </div>
        <div class="field">
          <label for="brain-model">Model</label>
          <input id="brain-model" v-model="model" type="text" autocomplete="off" list="brain-models" placeholder="gemma2:2b" />
          <datalist id="brain-models">
            <option v-for="item in models" :key="item.name" :value="item.name" />
          </datalist>
          <p class="field__hint">Use the exact Ollama tag. The default is gemma2:2b.</p>
        </div>
        <div class="brain-form__row">
          <div class="field">
            <label for="brain-timeout">Timeout (seconds)</label>
            <input id="brain-timeout" v-model.number="timeoutSeconds" type="number" min="5" max="600" />
          </div>
          <div class="field">
            <label for="brain-input">Max input characters</label>
            <input id="brain-input" v-model.number="maxInputChars" type="number" min="500" max="200000" step="500" />
          </div>
        </div>
        <p v-if="formError" class="brain-error" role="alert">{{ formError }}</p>
        <div class="brain-form__actions">
          <button class="btn btn--ghost" type="button" :disabled="testing" @click="testConnection">
            <span v-if="testing" class="spinner" />
            <Icon v-else name="pulse" :size="15" />
            Test unsaved values
          </button>
          <button class="btn btn--primary" type="submit" :disabled="saving">
            <span v-if="saving" class="spinner" />
            <Icon v-else name="check" :size="15" />
            Save and check
          </button>
        </div>

        <div v-if="testResult" class="test-result" :class="{ 'test-result--ok': testResult.ok }">
          <div class="row row--between">
            <strong>{{ testResult.ok ? 'The model answered' : 'The test did not pass' }}</strong>
            <span class="mono">{{ testResult.latencyMs }} ms</span>
          </div>
          <p>{{ testResult.message }}</p>
          <pre v-if="testResult.sample" class="test-result__sample">{{ testResult.sample }}</pre>
          <p class="test-result__meta mono">{{ testResult.model }} · {{ testResult.endpoint }}</p>
        </div>
      </form>
    </section>

    <aside class="stack">
      <section v-if="!session.aiOnline" class="panel panel--accent setup-panel">
        <header class="panel__head"><h2>Make the model ready</h2></header>
        <div class="panel__body stack">
          <p class="setup-panel__copy">
            Run Ollama yourself; GhostShift never downloads or installs software in the background. On a machine
            with Ollama installed:
          </p>
          <div class="command">
            <code>ollama serve</code>
            <button class="icon-btn" type="button" aria-label="Copy serve command" @click="copy('ollama serve')">
              <Icon name="copy" :size="14" />
            </button>
          </div>
          <div class="command">
            <code>ollama pull {{ installModel }}</code>
            <button class="icon-btn" type="button" aria-label="Copy pull command" @click="copy(`ollama pull ${installModel}`)">
              <Icon name="copy" :size="14" />
            </button>
          </div>
          <p class="field__hint">
            Then check the model again. If a different open-weight model is already installed, choose it from the list
            below instead. Deterministic features keep working without AI.
          </p>
        </div>
      </section>

      <section class="panel">
        <header class="panel__head">
          <h2>Installed models</h2>
          <span class="panel__hint">{{ models.length }} reported</span>
        </header>
        <div v-if="models.length" class="model-list">
          <div v-for="item in models" :key="item.name" class="model-row">
            <div class="model-row__copy">
              <strong>{{ item.name }}</strong>
              <span>
                {{ item.parameterSize || 'size unknown' }}
                <template v-if="item.quantization"> · {{ item.quantization }}</template>
                <template v-if="item.family"> · {{ item.family }}</template>
                <template v-if="item.sizeBytes"> · {{ bytes(item.sizeBytes) }}</template>
              </span>
            </div>
            <button
              class="btn btn--sm"
              :class="{ 'btn--primary': item.name === model }"
              type="button"
              @click="useModel(item.name)"
            >
              {{ item.name === model ? 'Selected' : 'Use' }}
            </button>
          </div>
        </div>
        <div v-else class="panel__body">
          <p class="muted small">
            {{ status?.reachable ? 'Ollama did not report any installed models.' : 'Start Ollama to list installed models.' }}
            GhostShift does not guess model names.
          </p>
        </div>
      </section>
    </aside>
  </div>

  <div class="brain-grid brain-grid--lower">
    <section class="panel">
      <header class="panel__head">
        <h2>What the model is allowed to do</h2>
        <span class="panel__hint">Guardrails</span>
      </header>
      <div class="panel__body guardrails">
        <div class="guardrail">
          <span class="guardrail__icon"><Icon name="spark" :size="16" /></span>
          <div>
            <h3>Suggest with bounded context</h3>
            <p>
              File classification sees names, extensions, and sizes only. Log interpretation sees grouped evidence
              inside a character limit. Workflow drafts see your task description and the fixed action catalog.
            </p>
          </div>
        </div>
        <div class="guardrail">
          <span class="guardrail__icon"><Icon name="shield" :size="16" /></span>
          <div>
            <h3>Treat every response as untrusted</h3>
            <p>
              Model output is parsed into a closed schema. Unknown actions, categories, or fields are rejected. Text
              inside files and logs is labelled as data, never followed as an instruction.
            </p>
          </div>
        </div>
        <div class="guardrail">
          <span class="guardrail__icon"><Icon name="lock" :size="16" /></span>
          <div>
            <h3>Never authorize or execute</h3>
            <p>
              Deterministic code validates paths, parameters, and permissions. A human approves workflow execution.
              The engine refuses deletes, silent overwrites, arbitrary shell commands, and paths outside registered
              folders.
            </p>
          </div>
        </div>
        <div class="guardrail">
          <span class="guardrail__icon"><Icon name="database" :size="16" /></span>
          <div>
            <h3>Keep secrets out</h3>
            <p>
              GhostShift does not store API tokens in SQLite or send files to a remote service. The Ollama endpoint is
              local by default. AI-assisted outputs are labelled and recorded with the model name.
            </p>
          </div>
        </div>
      </div>
    </section>

    <aside class="stack">
      <section class="panel">
        <header class="panel__head">
          <h2>Recent AI-assisted reports</h2>
          <span class="panel__hint">{{ reportsLoading ? 'Loading' : `${aiReports.length} shown` }}</span>
        </header>
        <div v-if="aiReports.length" class="ai-report-list">
          <RouterLink
            v-for="report in aiReports"
            :key="report.id"
            :to="{ name: 'ghost-dev', query: { report: report.id } }"
            class="ai-report"
          >
            <span class="ai-report__icon"><Icon name="file" :size="14" /></span>
            <span class="ai-report__copy">
              <strong>{{ report.title }}</strong>
              <span>{{ report.model || 'model not recorded' }} · {{ relativeTime(report.createdAt) }}</span>
            </span>
            <Icon name="chevron" :size="13" />
          </RouterLink>
        </div>
        <div v-else class="panel__body">
          <p class="muted small">
            No AI-assisted reports yet. Deterministic reports still appear in GhostDev and History.
          </p>
        </div>
      </section>

      <section class="panel panel--quiet">
        <header class="panel__head"><h2>Model facts</h2></header>
        <div class="panel__body fact-list">
          <p><span>Provider</span><strong>{{ session.settings?.model.provider || 'ollama' }}</strong></p>
          <p><span>Model</span><strong>{{ session.settings?.model.model || model || '—' }}</strong></p>
          <p><span>Timeout</span><strong>{{ session.settings?.model.timeoutSeconds ?? timeoutSeconds }} s</strong></p>
          <p><span>Input cap</span><strong>{{ (session.settings?.model.maxInputChars ?? maxInputChars).toLocaleString() }} chars</strong></p>
          <p><span>Last health</span><strong>{{ session.settings?.model.lastHealthStatus || 'unknown' }}</strong></p>
          <p><span>Last checked</span><strong>{{ session.settings?.model.lastHealthAt ? humanDate(session.settings.model.lastHealthAt) : '—' }}</strong></p>
        </div>
      </section>
    </aside>
  </div>

  <section class="prompt-section">
    <header class="prompt-section__head">
      <div>
        <p class="eyebrow">Prompt starting points</p>
        <h2>Draft a workflow, then inspect every step.</h2>
        <p>
          These descriptions open GhostReplay with the prompt filled in. Drafting still requires the local model, and
          nothing is saved or run until you review and approve it.
        </p>
      </div>
      <button class="panel__link" type="button" :disabled="refreshing" @click="refreshStatus(true)">
        Re-check the model <Icon name="refresh" :size="13" />
      </button>
    </header>
    <div class="prompt-grid">
      <RouterLink v-for="prompt in prompts" :key="prompt.title" :to="promptLink(prompt.text)" class="prompt-card">
        <span class="prompt-card__icon"><Icon :name="prompt.icon" :size="18" /></span>
        <div>
          <h3>{{ prompt.title }}</h3>
          <p>{{ prompt.text }}</p>
        </div>
        <Icon name="arrow" :size="15" class="prompt-card__arrow" />
      </RouterLink>
    </div>
  </section>

  <EmptyState
    v-if="!session.settings"
    icon="brain"
    title="Settings are unavailable"
    description="GhostShift could not load the local settings. The backend may be offline; deterministic modules remain safe, but model configuration needs the backend."
  />
</template>

<style scoped>
.brain-error {
  margin: 0 0 1rem;
  padding: 0.65rem 0.85rem;
  border: 1px solid color-mix(in srgb, var(--danger) 30%, var(--line));
  border-left: 2px solid var(--danger);
  border-radius: var(--radius-sm);
  color: var(--danger);
  font-size: 0.78rem;
  line-height: 1.5;
}
.brain-hero {
  padding: 1.3rem 1.4rem 1.1rem;
  margin-bottom: 1.3rem;
  border-left: 3px solid var(--accent);
  background: var(--surface);
}
.brain-hero--danger {
  border-left-color: var(--danger);
}
.brain-hero--warning {
  border-left-color: var(--warn);
}
.brain-hero__state {
  display: flex;
  align-items: flex-start;
  gap: 0.9rem;
}
.brain-hero__state > div {
  flex: 1;
  min-width: 0;
}
.brain-hero__dot {
  width: 0.55rem;
  height: 0.55rem;
  margin-top: 0.55rem;
  border-radius: 0;
  background: var(--danger);
  flex: none;
}
.brain-hero--success .brain-hero__dot {
  background: var(--ok);
}
.brain-hero--warning .brain-hero__dot {
  background: var(--warn);
}
.brain-hero h2 {
  margin: 0.35rem 0 0;
  font-family: var(--font-display);
  font-size: clamp(1.5rem, 2.7vw, 2rem);
  font-weight: 600;
  letter-spacing: -0.04em;
}
.brain-hero__state .mono {
  margin: 0.35rem 0 0;
  color: var(--text-faint);
  font-size: 0.62rem;
  overflow-wrap: anywhere;
}
.brain-hero__note {
  margin: 0.9rem 0 0;
  max-width: 90ch;
  color: var(--text-muted);
  font-size: 0.8rem;
  line-height: 1.6;
}
.brain-hero__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 1rem;
  border-top: 1px solid var(--line);
}
.brain-hero__stats > div {
  padding: 0.8rem 0.8rem 0 0;
  min-width: 0;
}
.brain-hero__stats span {
  display: block;
  color: var(--text-faint);
  font-family: var(--font-sans);
  font-size: 0.68rem;
  font-weight: 650;
  letter-spacing: 0;
  text-transform: none;
}
.brain-hero__stats strong {
  display: block;
  margin-top: 0.25rem;
  font-size: 0.86rem;
  font-weight: 650;
}
.brain-hero__date {
  font-family: var(--font-mono);
  font-size: 0.72rem !important;
  font-weight: 400 !important;
}
.brain-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(300px, 0.9fr);
  gap: 1.3rem;
  align-items: start;
  margin-bottom: 1.3rem;
}
.brain-grid--lower {
  grid-template-columns: minmax(0, 1.4fr) minmax(300px, 0.9fr);
}
.panel__hint {
  font-family: var(--font-sans);
  font-size: 0.72rem;
  color: var(--text-faint);
}
.brain-form {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}
.brain-form__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.9rem;
}
.brain-form__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.55rem;
  flex-wrap: wrap;
  padding-top: 0.2rem;
}
.test-result {
  padding: 0.75rem 0.85rem;
  border: 1px solid color-mix(in srgb, var(--danger) 25%, var(--line));
  border-left: 2px solid var(--danger);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--danger) 4%, transparent);
}
.test-result--ok {
  border-color: color-mix(in srgb, var(--ok) 25%, var(--line));
  border-left-color: var(--ok);
  background: color-mix(in srgb, var(--ok) 4%, transparent);
}
.test-result strong {
  font-size: 0.8rem;
}
.test-result .mono {
  color: var(--text-faint);
  font-size: 0.6rem;
}
.test-result p {
  margin: 0.4rem 0 0;
  color: var(--text-muted);
  font-size: 0.76rem;
  line-height: 1.5;
}
.test-result__sample {
  margin: 0.5rem 0 0;
  padding: 0.6rem 0.7rem;
  max-height: 7rem;
  overflow: auto;
  background: var(--ink);
  border: 1px solid var(--line);
  border-radius: 4px;
  font-size: 0.68rem;
  line-height: 1.5;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.test-result__meta {
  color: var(--text-faint);
  font-size: 0.58rem !important;
  overflow-wrap: anywhere;
}
.setup-panel__copy {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.78rem;
  line-height: 1.55;
}
.command {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.45rem 0.5rem 0.45rem 0.7rem;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--ink);
}
.command code {
  flex: 1;
  font-size: 0.72rem;
  color: var(--accent);
  overflow-wrap: anywhere;
}
.model-list {
  display: flex;
  flex-direction: column;
}
.model-row {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.65rem 0.8rem;
  border-bottom: 1px solid var(--line);
}
.model-row:last-child {
  border-bottom: 0;
}
.model-row__copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}
.model-row__copy strong {
  font-family: var(--font-mono);
  font-size: 0.7rem;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.model-row__copy span {
  margin-top: 0.12rem;
  font-size: 0.62rem;
  color: var(--text-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.guardrails {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}
.guardrail {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
}
.guardrail__icon {
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  border: 1px solid var(--line-strong);
  border-radius: 0;
  color: var(--signal);
  flex: none;
  margin-top: 0.05rem;
}
.guardrail h3 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 0.98rem;
  font-weight: 600;
  letter-spacing: -0.02em;
}
.guardrail p {
  margin: 0.3rem 0 0;
  color: var(--text-muted);
  font-size: 0.74rem;
  line-height: 1.55;
}
.ai-report-list {
  display: flex;
  flex-direction: column;
}
.ai-report {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  padding: 0.65rem 0.8rem;
  border-bottom: 1px solid var(--line);
  color: var(--text);
}
.ai-report:last-child {
  border-bottom: 0;
}
.ai-report:hover {
  background: var(--surface-2);
  color: var(--text);
}
.ai-report__icon {
  display: grid;
  place-items: center;
  width: 1.55rem;
  height: 1.55rem;
  border: 1px solid var(--line);
  border-radius: 0;
  color: var(--text-faint);
  flex: none;
  margin-top: 0.05rem;
}
.ai-report__copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}
.ai-report__copy strong {
  font-size: 0.75rem;
  font-weight: 550;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ai-report__copy span {
  margin-top: 0.12rem;
  font-family: var(--font-mono);
  font-size: 0.56rem;
  color: var(--text-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ai-report > .icon {
  color: var(--text-faint);
  margin-top: 0.25rem;
}
.fact-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.fact-list p {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  margin: 0;
  font-size: 0.73rem;
}
.fact-list span {
  color: var(--text-faint);
  font-family: var(--font-sans);
  font-size: 0.68rem;
  font-weight: 650;
  text-transform: none;
  letter-spacing: 0;
}
.fact-list strong {
  font-weight: 650;
  text-align: right;
  overflow-wrap: anywhere;
}
.prompt-section {
  margin-top: 1.7rem;
  padding-top: 1.4rem;
  border-top: 1px solid var(--line);
}
.prompt-section__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1.2rem;
  margin-bottom: 1rem;
}
.prompt-section__head h2 {
  margin: 0.4rem 0 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 2.4vw, 1.8rem);
  font-weight: 600;
  letter-spacing: -0.04em;
}
.prompt-section__head p {
  margin: 0.45rem 0 0;
  max-width: 68ch;
  color: var(--text-muted);
  font-size: 0.78rem;
  line-height: 1.55;
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
  white-space: nowrap;
}
.prompt-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
}
.prompt-card {
  display: flex;
  align-items: flex-start;
  gap: 0.7rem;
  padding: 0.95rem;
  border: 1px solid var(--line);
  border-radius: 0;
  background: var(--surface);
  box-shadow: 4px 4px 0 var(--shadow-color);
  color: var(--text);
  transition:
    border-color 150ms ease,
    background 150ms ease,
    transform 150ms var(--ease),
    box-shadow 150ms var(--ease);
}
.prompt-card:hover {
  border-color: var(--accent);
  background: var(--surface-2);
  color: var(--text);
  transform: translate(-2px, -2px);
  box-shadow: 6px 6px 0 color-mix(in srgb, var(--accent) 30%, transparent);
}
.prompt-card__icon {
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  border: 1px solid var(--line-strong);
  border-radius: 0;
  color: var(--signal);
  flex: none;
}
.prompt-card h3 {
  margin: 0.15rem 0 0;
  font-family: var(--font-display);
  font-size: 1rem;
  font-weight: 600;
  letter-spacing: -0.025em;
}
.prompt-card p {
  margin: 0.35rem 0 0;
  color: var(--text-muted);
  font-size: 0.76rem;
  line-height: 1.5;
}
.prompt-card__arrow {
  margin-left: auto;
  color: var(--text-faint);
  margin-top: 0.2rem;
  flex: none;
}

@media (max-width: 1150px) {
  .brain-grid,
  .brain-grid--lower {
    grid-template-columns: 1fr;
  }
  .prompt-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 720px) {
  .brain-hero__stats {
    grid-template-columns: 1fr 1fr;
  }
  .brain-hero__stats > div:nth-child(-n + 2) {
    border-bottom: 1px solid var(--line);
    padding-bottom: 0.75rem;
  }
  .brain-form__row,
  .guardrails {
    grid-template-columns: 1fr;
  }
  .prompt-section__head {
    flex-direction: column;
    align-items: flex-start;
  }
  .brain-hero__state {
    flex-wrap: wrap;
  }
}
</style>
