<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { downloadReport, api } from '@/api/client'
import type { FileInfo, ReportSummary, ReportView, ScanResult } from '@/api/types'
import { errorMessage } from '@/lib/errors'
import { bytes, humanDate, relativeTime } from '@/lib/format'
import { useSessionStore } from '@/stores/session'
import { useToastStore } from '@/stores/toast'
import DirectoryPicker from '@/components/DirectoryPicker.vue'
import EmptyState from '@/components/EmptyState.vue'
import Icon from '@/components/Icon.vue'
import MarkdownLite from '@/components/MarkdownLite.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'

const session = useSessionStore()
const toast = useToastStore()
const route = useRoute()

const directoryId = ref<number | null>(null)
const recursive = ref(false)
const useAi = ref(false)
const scanning = ref(false)
const analyzing = ref(false)
const error = ref('')
const scan = ref<ScanResult | null>(null)
const selectedFile = ref('')
const reports = ref<ReportSummary[]>([])
const reportsLoading = ref(false)
const report = ref<ReportView | null>(null)
const reportLoading = ref(false)

const TEXT_EXTENSIONS = new Set([
  '.log',
  '.txt',
  '.out',
  '.err',
  '.trace',
  '.json',
  '.csv',
  '.tsv',
  '.md',
  '.yaml',
  '.yml',
  '.conf',
  '.properties',
])

const analyzable = computed(() => (scan.value?.files ?? []).filter((file) => TEXT_EXTENSIONS.has(file.extension)))
const selectedFileInfo = computed(() => analyzable.value.find((file) => file.relativePath === selectedFile.value))
const evidence = computed(() => report.value?.evidence ?? null)
const aiNotice = computed(() => evidence.value?.aiNotice ?? '')
const selectedDirectory = computed(
  () => session.directories.find((directory) => directory.id === directoryId.value) ?? null,
)

function extension(file: FileInfo): string {
  return file.extension || 'file'
}

async function loadReports() {
  reportsLoading.value = true
  try {
    reports.value = await api.reports(50)
  } catch (cause) {
    error.value = errorMessage(cause, 'Reports could not be loaded.')
  } finally {
    reportsLoading.value = false
  }
}

async function runScan() {
  if (!directoryId.value) {
    error.value = 'Choose a registered folder to scan.'
    return
  }
  scanning.value = true
  error.value = ''
  scan.value = null
  selectedFile.value = ''
  try {
    scan.value = await api.scan(directoryId.value, recursive.value)
    const first = scan.value.files.find((file) => TEXT_EXTENSIONS.has(file.extension))
    if (first) selectedFile.value = first.relativePath
    toast.success('Folder scanned', `${scan.value.fileCount} file(s) read within the folder boundary.`)
  } catch (cause) {
    error.value = errorMessage(cause, 'The folder could not be scanned.')
  } finally {
    scanning.value = false
  }
}

async function analyze() {
  if (!directoryId.value || !selectedFile.value) {
    error.value = 'Choose a folder and a text file first.'
    return
  }
  analyzing.value = true
  error.value = ''
  try {
    report.value = await api.analyzeDeveloper({
      directoryId: directoryId.value,
      relativePath: selectedFile.value,
      useAi: useAi.value,
    })
    await loadReports()
    toast.success('Evidence report saved', report.value.aiUsed ? 'The local model interpretation is labelled.' : 'Deterministic evidence only.')
  } catch (cause) {
    error.value = errorMessage(cause, 'That file could not be analyzed.')
  } finally {
    analyzing.value = false
  }
}

async function openReport(id: string) {
  reportLoading.value = true
  error.value = ''
  try {
    report.value = await api.report(id)
  } catch (cause) {
    error.value = errorMessage(cause, 'That report could not be opened.')
  } finally {
    reportLoading.value = false
  }
}

function exportCurrent() {
  if (!report.value) return
  const summary: ReportSummary = {
    id: report.value.id,
    kind: report.value.kind,
    title: report.value.title,
    sourceRelative: report.value.sourceRelative,
    aiUsed: report.value.aiUsed,
    model: report.value.model,
    createdAt: report.value.createdAt,
  }
  void downloadReport(summary, report.value.contentMarkdown).catch((cause) => {
    error.value = errorMessage(cause, 'The report could not be exported.')
  })
}

onMounted(async () => {
  if (session.directories.length === 0) await session.refreshDirectories().catch(() => undefined)
  await loadReports()
  const queryId = typeof route.query.report === 'string' ? route.query.report : ''
  if (queryId) await openReport(queryId)
  else if (reports.value.length > 0) await openReport(reports.value[0].id)
})
</script>

<template>
  <PageHeader
    eyebrow="GhostDev"
    title="Read the evidence before the theory."
    description="Point GhostDev at a log or text file. Deterministic parsing finds the signatures; the local model may interpret them, and every fallback is stated plainly."
  >
    <template #actions>
      <button class="btn btn--ghost btn--sm" type="button" :disabled="reportsLoading" @click="loadReports">
        <span v-if="reportsLoading" class="spinner" />
        <Icon v-else name="refresh" :size="15" />
        Refresh reports
      </button>
    </template>
  </PageHeader>

  <p v-if="error" class="dev-error" role="alert">{{ error }}</p>

  <section class="panel dev-setup">
    <div class="dev-setup__intro">
      <span class="eyebrow">Inspect a file</span>
      <h2>Analyze without executing anything.</h2>
      <p>
        Reading is bounded and stays inside the registered folder. GhostDev never runs commands from log contents and
        never edits the source file.
      </p>
    </div>
    <div class="dev-setup__form">
      <DirectoryPicker
        v-model="directoryId"
        label="Folder containing the log"
        hint="Only registered folders are readable. Symlinks that escape the folder are refused."
      />
      <div class="dev-setup__row">
        <label class="check">
          <input v-model="recursive" type="checkbox" />
          Include subfolders in scan
        </label>
        <label class="check">
          <input v-model="useAi" type="checkbox" />
          Ask GhostBrain to interpret evidence
        </label>
        <button class="btn" type="button" :disabled="scanning || !directoryId" @click="runScan">
          <span v-if="scanning" class="spinner" />
          <Icon v-else name="search" :size="15" />
          Scan folder
        </button>
      </div>
      <p v-if="useAi && !session.aiOnline" class="dev-warning">
        <Icon name="alert" :size="13" />
        GhostBrain is not ready. The backend will return a deterministic report and state that AI was not used.
      </p>

      <div v-if="scan" class="dev-scan">
        <div class="dev-scan__head">
          <span class="mono">{{ scan.fileCount }} files scanned</span>
          <span class="mono">{{ analyzable.length }} text-like</span>
          <span v-if="scan.truncated" class="chip chip--accent">Scan limit reached</span>
          <span v-if="scan.skippedSymlinks" class="chip">{{ scan.skippedSymlinks }} symlinks skipped</span>
        </div>
        <div v-if="analyzable.length" class="dev-picker">
          <label for="dev-file">Text file</label>
          <select id="dev-file" v-model="selectedFile">
            <option v-for="file in analyzable" :key="file.id" :value="file.relativePath">
              {{ file.relativePath }} · {{ bytes(file.sizeBytes) }}
            </option>
          </select>
          <button class="btn btn--primary" type="button" :disabled="analyzing || !selectedFile" @click="analyze">
            <span v-if="analyzing" class="spinner" />
            <Icon v-else name="bug" :size="15" />
            Analyze log
          </button>
        </div>
        <p v-else class="dev-warning">
          <Icon name="alert" :size="13" />
          No .log, .txt, .json, .csv, or Markdown files were found in this scan.
        </p>
        <p v-if="selectedFileInfo" class="field__hint">
          Reading {{ selectedFileInfo.relativePath }} · modified {{ relativeTime(selectedFileInfo.modifiedAt) }}.
          Binary files are refused.
        </p>
      </div>
      <p v-else-if="selectedDirectory" class="field__hint">
        Scan {{ selectedDirectory.displayName }} to choose a file.
      </p>
    </div>
  </section>

  <div class="split-aside dev-main">
    <section class="panel dev-report">
      <header class="panel__head">
        <h2>{{ report ? 'Incident evidence' : 'Report' }}</h2>
        <div v-if="report" class="row">
          <span class="chip" :class="{ 'chip--accent': report.aiUsed }">
            {{ report.aiUsed ? 'GhostBrain assisted' : 'Deterministic only' }}
          </span>
          <button class="btn btn--sm btn--ghost" type="button" @click="exportCurrent">
            <Icon name="download" :size="14" />
            Export .md
          </button>
        </div>
      </header>

      <div v-if="reportLoading" class="dev-loading"><span class="spinner" /> Loading report…</div>

      <template v-else-if="report">
        <div class="dev-report__head">
          <h2>{{ report.title }}</h2>
          <p class="mono">
            {{ report.sourceRelative || 'Workflow report' }} · {{ humanDate(report.createdAt) }}
            <template v-if="report.model"> · {{ report.model }}</template>
          </p>
        </div>

        <div v-if="evidence" class="dev-stats">
          <div>
            <span>Lines read</span>
            <strong>{{ evidence.analysis.totalLines }}</strong>
          </div>
          <div>
            <span>Errors</span>
            <strong class="dev-stats__danger">{{ evidence.analysis.errorLines }}</strong>
          </div>
          <div>
            <span>Warnings</span>
            <strong class="dev-stats__warn">{{ evidence.analysis.warningLines }}</strong>
          </div>
          <div>
            <span>Signatures</span>
            <strong>{{ evidence.analysis.groups?.length ?? 0 }}</strong>
          </div>
          <div>
            <span>Bytes read</span>
            <strong>{{ bytes(evidence.analysis.bytesRead) }}</strong>
          </div>
        </div>

        <div v-if="aiNotice" class="dev-ai-notice" :class="{ 'dev-ai-notice--warn': !report.aiUsed }">
          <Icon :name="report.aiUsed ? 'brain' : 'shield'" :size="16" />
          <p>{{ aiNotice }}</p>
        </div>

        <div v-if="evidence" class="dev-evidence">
          <section v-if="evidence.analysis.observations?.length" class="dev-section">
            <h3>Observed evidence</h3>
            <ul class="dev-list">
              <li v-for="(observation, index) in evidence.analysis.observations" :key="index">{{ observation }}</li>
            </ul>
          </section>

          <section class="dev-section">
            <h3>Grouped signatures</h3>
            <div v-if="evidence.analysis.groups?.length" class="signature-list">
              <article v-for="group in evidence.analysis.groups" :key="group.signature" class="signature">
                <div class="signature__head">
                  <span class="signature__count">{{ group.count }}×</span>
                  <span class="signature__lines mono">lines {{ group.lineNumbers.join(', ') }}</span>
                </div>
                <p class="signature__sample">{{ group.sample }}</p>
                <p v-if="group.firstSeen || group.lastSeen" class="signature__time mono">
                  {{ group.firstSeen || '—' }} → {{ group.lastSeen || '—' }}
                </p>
              </article>
            </div>
            <p v-else class="muted small">No grouped error signatures were found in the readable portion.</p>
          </section>

          <div class="dev-columns">
            <section class="dev-section">
              <h3>Possible causes <span class="faint">(hypotheses)</span></h3>
              <ul v-if="evidence.possibleCauses?.length" class="dev-list">
                <li v-for="(cause, index) in evidence.possibleCauses" :key="index">{{ cause }}</li>
              </ul>
              <p v-else class="muted small">No cause was inferred from the available evidence.</p>
            </section>
            <section class="dev-section">
              <h3>Recommended checks</h3>
              <ol v-if="evidence.recommendedChecks?.length" class="dev-list">
                <li v-for="(check, index) in evidence.recommendedChecks" :key="index">{{ check }}</li>
              </ol>
              <p v-else class="muted small">No follow-up checks were generated.</p>
            </section>
          </div>
        </div>

        <details class="dev-markdown">
          <summary>Full Markdown report</summary>
          <div class="dev-markdown__body">
            <MarkdownLite :content="report.contentMarkdown" />
          </div>
        </details>
      </template>

      <div v-else class="panel__body">
        <EmptyState
          icon="bug"
          title="No report selected"
          description="Scan a folder, choose a text log, and analyze it. GhostShift saves the report locally and never changes the source file."
        />
      </div>
    </section>

    <aside class="stack">
      <section class="panel">
        <header class="panel__head">
          <h2>Saved reports</h2>
          <span class="panel__count">{{ reports.length }}</span>
        </header>
        <div v-if="reports.length" class="report-list">
          <button
            v-for="item in reports"
            :key="item.id"
            class="report-row"
            :class="{ 'report-row--active': report?.id === item.id }"
            type="button"
            @click="openReport(item.id)"
          >
            <span class="report-row__icon"><Icon name="file" :size="14" /></span>
            <span class="report-row__copy">
              <strong>{{ item.title }}</strong>
              <span>{{ item.sourceRelative || item.kind }} · {{ relativeTime(item.createdAt) }}</span>
            </span>
            <span v-if="item.aiUsed" class="report-row__ai">AI</span>
          </button>
        </div>
        <div v-else class="panel__body">
          <p class="muted small">No reports yet. Analysis is real and saved locally; nothing is pre-filled.</p>
        </div>
      </section>

      <section class="panel panel--quiet">
        <header class="panel__head"><h2>Evidence rules</h2></header>
        <div class="panel__body dev-rules">
          <p><Icon name="shield" :size="13" /> Reads are capped at the configured byte limit; large files are read from the tail.</p>
          <p><Icon name="lock" :size="13" /> Paths are resolved inside the registered folder only.</p>
          <p><Icon name="brain" :size="13" /> The model sees grouped evidence, not your whole disk.</p>
          <p><Icon name="terminal" :size="13" /> No commands are executed and no files are modified.</p>
        </div>
      </section>
    </aside>
  </div>
</template>

<style scoped>
.dev-error {
  margin: 0 0 1.1rem;
  padding: 0.7rem 0.9rem;
  border: 1px solid color-mix(in srgb, var(--danger) 30%, var(--line));
  border-left: 2px solid var(--danger);
  border-radius: var(--radius-sm);
  color: var(--danger);
  font-size: 0.8rem;
}
.dev-setup {
  display: grid;
  grid-template-columns: minmax(240px, 0.8fr) minmax(0, 1.5fr);
  gap: 1.8rem;
  padding: 1.4rem 1.5rem;
  margin-bottom: 1.4rem;
  background:
    linear-gradient(140deg, color-mix(in srgb, var(--info) 4%, transparent), transparent 45%),
    var(--surface);
}
.dev-setup__intro h2 {
  margin: 0.45rem 0 0;
  font-family: var(--font-display);
  font-size: clamp(1.35rem, 2vw, 1.7rem);
  font-weight: 470;
  letter-spacing: -0.02em;
}
.dev-setup__intro p {
  margin: 0.55rem 0 0;
  max-width: 42ch;
  color: var(--text-muted);
  font-size: 0.8rem;
  line-height: 1.6;
}
.dev-setup__form {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  min-width: 0;
}
.dev-setup__row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
}
.dev-setup__row .btn {
  margin-left: auto;
}
.dev-warning {
  display: flex;
  align-items: flex-start;
  gap: 0.35rem;
  margin: 0;
  color: var(--warn);
  font-size: 0.73rem;
  line-height: 1.45;
}
.dev-warning .icon {
  margin-top: 0.08rem;
}
.dev-scan {
  padding-top: 0.8rem;
  border-top: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}
.dev-scan__head {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  flex-wrap: wrap;
  color: var(--text-faint);
  font-size: 0.6rem;
}
.dev-picker {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 0.45rem;
  align-items: end;
}
.dev-picker label {
  grid-column: 1 / -1;
  font-family: var(--font-mono);
  font-size: 0.62rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-muted);
}
.dev-picker select {
  min-width: 0;
}
.dev-main {
  align-items: start;
}
.dev-report__head {
  padding: 1.2rem 1.3rem 0;
}
.dev-report__head h2 {
  margin: 0;
  font-family: var(--font-display);
  font-size: 1.55rem;
  font-weight: 470;
  letter-spacing: -0.02em;
}
.dev-report__head p {
  margin: 0.4rem 0 0;
  color: var(--text-faint);
  font-size: 0.63rem;
  overflow-wrap: anywhere;
}
.dev-stats {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  margin: 1rem 1.3rem;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}
.dev-stats > div {
  padding: 0.65rem 0.75rem;
  border-right: 1px solid var(--line);
}
.dev-stats > div:last-child {
  border-right: 0;
}
.dev-stats span {
  display: block;
  font-family: var(--font-mono);
  font-size: 0.56rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-faint);
}
.dev-stats strong {
  display: block;
  margin-top: 0.25rem;
  font-family: var(--font-display);
  font-size: 1.25rem;
  font-weight: 450;
  line-height: 1;
}
.dev-stats__danger {
  color: var(--danger);
}
.dev-stats__warn {
  color: var(--warn);
}
.dev-ai-notice {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  margin: 0 1.3rem 1rem;
  padding: 0.7rem 0.8rem;
  border: 1px solid color-mix(in srgb, var(--accent) 20%, var(--line));
  border-left: 2px solid var(--accent);
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  background: color-mix(in srgb, var(--accent) 4%, transparent);
}
.dev-ai-notice--warn {
  border-left-color: var(--warn);
  border-color: color-mix(in srgb, var(--warn) 20%, var(--line));
  background: color-mix(in srgb, var(--warn) 4%, transparent);
}
.dev-ai-notice .icon {
  color: var(--accent);
  margin-top: 0.1rem;
  flex: none;
}
.dev-ai-notice--warn .icon {
  color: var(--warn);
}
.dev-ai-notice p {
  margin: 0;
  font-size: 0.76rem;
  line-height: 1.5;
}
.dev-evidence {
  padding: 0 1.3rem 1.2rem;
  display: flex;
  flex-direction: column;
  gap: 1.2rem;
}
.dev-section h3 {
  margin: 0 0 0.5rem;
  font-size: 0.78rem;
  font-weight: 600;
  letter-spacing: -0.005em;
}
.dev-section h3 .faint {
  font-weight: 400;
  font-size: 0.9em;
}
.dev-list {
  margin: 0;
  padding-left: 1.15rem;
  color: var(--text-muted);
  font-size: 0.78rem;
  line-height: 1.6;
}
.dev-list li {
  margin: 0.3rem 0;
}
.signature-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.signature {
  padding: 0.65rem 0.75rem;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--surface-2) 50%, transparent);
}
.signature__head {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}
.signature__count {
  font-family: var(--font-mono);
  font-size: 0.68rem;
  color: var(--danger);
}
.signature__lines {
  color: var(--text-faint);
  font-size: 0.58rem;
}
.signature__sample {
  margin: 0.35rem 0 0;
  font-family: var(--font-mono);
  font-size: 0.68rem;
  line-height: 1.5;
  color: var(--text-muted);
  overflow-wrap: anywhere;
}
.signature__time {
  margin: 0.35rem 0 0;
  font-size: 0.56rem;
  color: var(--text-faint);
}
.dev-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.2rem;
}
.dev-markdown {
  border-top: 1px solid var(--line);
  padding: 0.8rem 1.3rem 1.2rem;
}
.dev-markdown summary {
  cursor: pointer;
  font-family: var(--font-mono);
  font-size: 0.64rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--text-muted);
}
.dev-markdown summary:hover {
  color: var(--text);
}
.dev-markdown__body {
  margin-top: 0.9rem;
  padding-top: 0.9rem;
  border-top: 1px solid var(--line);
}
.dev-loading {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 1.2rem;
  color: var(--text-muted);
  font-size: 0.8rem;
}
.report-list {
  display: flex;
  flex-direction: column;
  max-height: 420px;
  overflow-y: auto;
}
.report-row {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  padding: 0.6rem 0.8rem;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  text-align: left;
  color: var(--text);
}
.report-row:last-child {
  border-bottom: 0;
}
.report-row:hover {
  background: var(--surface-2);
}
.report-row--active {
  background: color-mix(in srgb, var(--accent) 5%, var(--surface-2));
}
.report-row__icon {
  display: grid;
  place-items: center;
  width: 1.5rem;
  height: 1.5rem;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--text-faint);
  flex: none;
  margin-top: 0.08rem;
}
.report-row__copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}
.report-row__copy strong {
  font-size: 0.74rem;
  font-weight: 550;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.report-row__copy span {
  margin-top: 0.12rem;
  font-family: var(--font-mono);
  font-size: 0.56rem;
  color: var(--text-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.report-row__ai {
  font-family: var(--font-mono);
  font-size: 0.54rem;
  letter-spacing: 0.08em;
  color: var(--accent);
  padding-top: 0.18rem;
}
.panel__count {
  font-family: var(--font-mono);
  font-size: 0.62rem;
  color: var(--text-faint);
}
.dev-rules {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}
.dev-rules p {
  display: flex;
  align-items: flex-start;
  gap: 0.4rem;
  margin: 0;
  font-size: 0.73rem;
  line-height: 1.45;
  color: var(--text-muted);
}
.dev-rules .icon {
  margin-top: 0.05rem;
  color: var(--text-faint);
  flex: none;
}

@media (max-width: 1100px) {
  .dev-setup {
    grid-template-columns: 1fr;
    gap: 1.2rem;
  }
  .dev-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .dev-stats > div:nth-child(3) {
    border-right: 0;
  }
  .dev-stats > div:nth-child(-n + 3) {
    border-bottom: 1px solid var(--line);
  }
}
@media (max-width: 620px) {
  .dev-columns {
    grid-template-columns: 1fr;
  }
  .dev-picker {
    grid-template-columns: 1fr;
  }
  .dev-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .dev-stats > div:nth-child(odd) {
    border-right: 1px solid var(--line);
  }
  .dev-stats > div:nth-child(even) {
    border-right: 0;
  }
  .dev-stats > div:nth-child(-n + 4) {
    border-bottom: 1px solid var(--line);
  }
}
</style>
