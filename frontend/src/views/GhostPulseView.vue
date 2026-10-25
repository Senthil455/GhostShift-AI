<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '@/api/client'
import type { DuplicateGroup, DuplicateReport, Insight, TimeSavingsView } from '@/api/types'
import { errorMessage } from '@/lib/errors'
import { bytes, duration, humanDate, minutes, plural, relativeTime } from '@/lib/format'
import { useSessionStore } from '@/stores/session'
import { useToastStore } from '@/stores/toast'
import DirectoryPicker from '@/components/DirectoryPicker.vue'
import EmptyState from '@/components/EmptyState.vue'
import Icon from '@/components/Icon.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'

const session = useSessionStore()
const toast = useToastStore()

const savings = ref<TimeSavingsView | null>(null)
const savingsLoading = ref(false)
const savingsError = ref('')

const directoryId = ref<number | null>(null)
const recursive = ref(true)
const duplicateLoading = ref(false)
const duplicateError = ref('')
const duplicateReport = ref<DuplicateReport | null>(null)

const insights = computed<Insight[]>(() => session.dashboard?.insights ?? [])
const maxManual = computed(() =>
  Math.max(1, ...(savings.value?.lines ?? []).map((line) => line.manualMinutes)),
)
const totalReclaimable = computed(() =>
  (duplicateReport.value?.groups ?? [])
    .filter((group) => group.kind === 'EXACT')
    .reduce((total, group) => total + group.sizeBytes * Math.max(0, group.files.length - 1), 0),
)
const duplicateGroupCount = computed(() => duplicateReport.value?.groups?.length ?? 0)
const exactGroupCount = computed(
  () => duplicateReport.value?.groups?.filter((group) => group.kind === 'EXACT').length ?? 0,
)

function signedMinutes(value: number): string {
  const sign = value < 0 ? '−' : ''
  return `${sign}${minutes(Math.abs(value))}`
}

function barWidth(value: number): string {
  return `${Math.min(100, Math.max(1, Math.round((value / maxManual.value) * 100)))}%`
}

function measuredWidth(line: { manualMinutes: number; actualMinutes: number }): string {
  if (line.manualMinutes <= 0) return '0%'
  return `${Math.min(100, Math.max(0, Math.round((line.actualMinutes / line.manualMinutes) * 100)))}%`
}

function groupOverlap(group: DuplicateGroup): number {
  if (group.kind !== 'EXACT') return 0
  return group.sizeBytes * Math.max(0, group.files.length - 1)
}

function insightIcon(severity: string): string {
  if (severity === 'ATTENTION') return 'alert'
  if (severity === 'WARNING') return 'shield'
  return 'spark'
}

function insightTone(severity: string): string {
  if (severity === 'ATTENTION') return 'danger'
  if (severity === 'WARNING') return 'warning'
  return 'neutral'
}

async function loadSavings() {
  savingsLoading.value = true
  savingsError.value = ''
  try {
    savings.value = await api.timeSavings()
  } catch (cause) {
    savingsError.value = errorMessage(cause, 'The local metrics could not be calculated.')
  } finally {
    savingsLoading.value = false
  }
}

async function refreshSystem() {
  try {
    const [health, system] = await Promise.all([api.health(), api.systemInfo()])
    session.health = health
    session.system = system
  } catch {
    // Status cards keep the last known values when the backend is briefly unavailable.
  }
}

async function refresh() {
  await Promise.all([loadSavings(), session.refreshDashboard().catch(() => undefined), refreshSystem()])
}

async function inspectDuplicates() {
  if (!directoryId.value) {
    duplicateError.value = 'Choose a registered folder before scanning for duplicates.'
    return
  }
  duplicateLoading.value = true
  duplicateError.value = ''
  duplicateReport.value = null
  try {
    duplicateReport.value = await api.duplicates(directoryId.value, recursive.value)
    toast.success(
      'Duplicate scan complete',
      `${duplicateReport.value.groups.length} ${plural(duplicateReport.value.groups.length, 'group')} found. Nothing was deleted or changed.`,
    )
  } catch (cause) {
    duplicateError.value = errorMessage(cause, 'The duplicate scan could not be completed.')
  } finally {
    duplicateLoading.value = false
  }
}

onMounted(async () => {
  if (session.directories.length === 0) {
    await session.refreshDirectories().catch(() => undefined)
  }
  await refresh()
})
</script>

<template>
  <PageHeader
    eyebrow="GhostPulse"
    title="Measure the shift, honestly."
    description="Local activity, assumption-based time savings, and read-only duplicate evidence. GhostPulse uses your history and clearly labels every estimate."
  >
    <template #actions>
      <RouterLink to="/history" class="btn btn--ghost btn--sm">
        <Icon name="clock" :size="14" />
        Open history
      </RouterLink>
      <button class="btn btn--primary btn--sm" type="button" :disabled="savingsLoading" @click="refresh">
        <span v-if="savingsLoading" class="spinner" />
        <Icon v-else name="refresh" :size="15" />
        Refresh metrics
      </button>
    </template>
  </PageHeader>

  <p v-if="savingsError" class="pulse-error" role="alert">{{ savingsError }}</p>

  <section v-if="savings" class="savings-hero panel">
    <div class="savings-hero__main">
      <p class="eyebrow">Estimated time returned</p>
      <p class="savings-hero__value numeric">{{ signedMinutes(savings.estimatedMinutesSaved) }}</p>
      <p class="savings-hero__copy">
        Based on your configured assumptions and GhostShift's own records. Actual time is measured only where a
        plan or run records a duration.
      </p>
      <div class="savings-hero__method">
        <span class="chip chip--accent">{{ savings.method.replaceAll('_', ' ') }}</span>
        <span class="mono">manual {{ minutes(savings.manualMinutes) }} · measured {{ minutes(savings.actualMinutes) }}</span>
      </div>
    </div>
    <div class="savings-hero__aside">
      <div class="savings-figure">
        <span>Manual estimate</span>
        <strong class="numeric">{{ minutes(savings.manualMinutes) }}</strong>
      </div>
      <div class="savings-figure">
        <span>Actually measured</span>
        <strong class="numeric">{{ minutes(savings.actualMinutes) }}</strong>
      </div>
      <div class="savings-figure">
        <span>Records included</span>
        <strong class="numeric">{{ session.dashboard?.totalRuns ?? 0 }} runs</strong>
      </div>
      <p class="savings-hero__disclaimer">{{ savings.disclaimer }}</p>
    </div>
  </section>

  <div v-else-if="savingsLoading" class="pulse-loading">
    <div class="skeleton" style="height: 11rem" />
    <div class="skeleton" style="height: 9rem" />
  </div>

  <div class="pulse-grid">
    <section class="panel savings-breakdown">
      <header class="panel__head">
        <h2>Where the estimate comes from</h2>
        <RouterLink to="/settings" class="panel__link">
          Edit assumptions <Icon name="arrow" :size="13" />
        </RouterLink>
      </header>
      <div v-if="savings?.lines?.length" class="savings-lines">
        <div v-for="line in savings.lines" :key="line.label" class="savings-line">
          <div class="savings-line__head">
            <div>
              <strong>{{ line.label }}</strong>
              <span class="savings-line__numbers mono">
                {{ minutes(line.manualMinutes) }} manual · {{ minutes(line.actualMinutes) }} measured
              </span>
            </div>
            <span class="savings-line__saved" :class="{ 'savings-line__saved--negative': line.savedMinutes < 0 }">
              {{ signedMinutes(line.savedMinutes) }}
            </span>
          </div>
          <div class="savings-bar" :aria-label="`${line.label}: manual estimate versus measured time`">
            <span class="savings-bar__manual" :style="{ width: barWidth(line.manualMinutes) }" />
            <span class="savings-bar__measured" :style="{ width: measuredWidth(line) }" />
          </div>
          <p class="savings-line__basis">{{ line.basis }}</p>
        </div>
        <div class="savings-legend">
          <span><i class="legend-manual" /> Manual assumption</span>
          <span><i class="legend-measured" /> Measured duration</span>
        </div>
      </div>
      <div v-else class="panel__body">
        <EmptyState
          compact
          icon="pulse"
          title="No activity to estimate yet"
          description="Run a plan, analyze a log, or complete a workflow. GhostPulse will then show what the assumptions produce."
        />
      </div>
    </section>

    <section class="panel insights-panel">
      <header class="panel__head">
        <h2>Open signals</h2>
        <span class="panel__hint">Deterministic</span>
      </header>
      <div v-if="insights.length" class="pulse-insights">
        <div v-for="insight in insights" :key="insight.title" class="pulse-insight">
          <span class="pulse-insight__icon" :class="`pulse-insight__icon--${insightTone(insight.severity)}`">
            <Icon :name="insightIcon(insight.severity)" :size="15" />
          </span>
          <div>
            <strong>{{ insight.title }}</strong>
            <p>{{ insight.detail }}</p>
          </div>
        </div>
      </div>
      <div v-else class="panel__body">
        <p class="muted small">
          No open signals. That means no pending approvals, no failed runs, and no setup warnings were found.
        </p>
      </div>
    </section>
  </div>

  <section class="panel duplicate-panel">
    <header class="panel__head">
      <div>
        <h2>Duplicate evidence</h2>
        <p class="duplicate-panel__sub">A read-only investigation. GhostShift never deletes or merges duplicates.</p>
      </div>
      <span class="panel__hint">SHA-256 · names</span>
    </header>
    <div class="panel__body duplicate-form">
      <DirectoryPicker
        v-model="directoryId"
        label="Folder to inspect"
        hint="The scan hashes files up to 100 MB. Nothing is moved, renamed, or deleted."
      />
      <div class="duplicate-form__options">
        <label class="check">
          <input v-model="recursive" type="checkbox" />
          Include subfolders
        </label>
        <button
          class="btn btn--primary"
          type="button"
          :disabled="duplicateLoading || !directoryId"
          @click="inspectDuplicates"
        >
          <span v-if="duplicateLoading" class="spinner" />
          <Icon v-else name="copy" :size="15" />
          Scan for duplicates
        </button>
      </div>
      <p v-if="duplicateError" class="pulse-error pulse-error--flush" role="alert">{{ duplicateError }}</p>
    </div>

    <div v-if="duplicateReport" class="duplicate-report">
      <div class="duplicate-report__summary">
        <div>
          <span>Files hashed</span>
          <strong class="numeric">{{ duplicateReport.filesHashed }}</strong>
        </div>
        <div>
          <span>Groups found</span>
          <strong class="numeric">{{ duplicateGroupCount }}</strong>
        </div>
        <div>
          <span>Exact-content groups</span>
          <strong class="numeric">{{ exactGroupCount }}</strong>
        </div>
        <div>
          <span>Potential exact overlap</span>
          <strong class="numeric">{{ bytes(totalReclaimable) }}</strong>
        </div>
        <div>
          <span>Large files skipped</span>
          <strong class="numeric">{{ duplicateReport.skippedLargeFiles }}</strong>
        </div>
      </div>
      <p class="notice duplicate-report__notice">
        <Icon name="lock" :size="15" class="notice__icon" />
        <span>{{ duplicateReport.notice }} Potential overlap is a size calculation, not a promise of reclaimed space.</span>
      </p>

      <div v-if="duplicateReport.groups?.length" class="duplicate-groups">
        <article v-for="(group, index) in duplicateReport.groups" :key="`${group.kind}-${group.label}-${index}`" class="duplicate-group">
          <header class="duplicate-group__head">
            <div>
              <span class="chip" :class="{ 'chip--accent': group.kind === 'EXACT' }">
                {{ group.kind === 'EXACT' ? 'Exact content' : 'Similar name' }}
              </span>
              <strong>{{ group.label }}</strong>
            </div>
            <div class="duplicate-group__size">
              <span class="mono">{{ group.files.length }} {{ plural(group.files.length, 'file') }} · {{ bytes(group.sizeBytes) }} each</span>
              <span v-if="group.kind === 'EXACT'" class="duplicate-group__overlap numeric">
                {{ bytes(groupOverlap(group)) }} overlap
              </span>
              <span v-else class="faint tiny">Not proof of identical content</span>
            </div>
          </header>
          <ul class="duplicate-files">
            <li v-for="file in group.files" :key="file.relativePath">
              <Icon name="file" :size="13" />
              <span class="duplicate-files__path mono">{{ file.relativePath }}</span>
              <span class="duplicate-files__meta">{{ bytes(file.sizeBytes) }} · {{ relativeTime(file.modifiedAt) }}</span>
            </li>
          </ul>
        </article>
      </div>
      <div v-else class="panel__body">
        <EmptyState
          compact
          icon="check"
          title="No duplicate groups found"
          description="No exact-content or strongly similar-name groups were found in the readable files. Nothing was changed."
        />
      </div>
    </div>
    <div v-else class="duplicate-placeholder">
      <p>
        Choose a folder to inspect. Exact groups are confirmed with SHA-256. Similar-name groups are only a hint and
        are never treated as proof that files are identical.
      </p>
    </div>
  </section>

  <section class="panel system-panel">
    <header class="panel__head">
      <h2>Local system</h2>
      <button class="panel__link" type="button" @click="refreshSystem">
        Re-check <Icon name="refresh" :size="13" />
      </button>
    </header>
    <div class="system-grid">
      <div class="system-fact">
        <span>Backend</span>
        <strong>
          <StatusPill :status="session.health?.status ?? 'UNKNOWN'" dot />
        </strong>
        <p>{{ session.health ? `Uptime ${duration((session.health.uptimeSeconds ?? 0) * 1000)}` : 'Backend status unavailable' }}</p>
      </div>
      <div class="system-fact">
        <span>Database</span>
        <strong>{{ session.health?.database ?? 'Unknown' }}</strong>
        <p>SQLite · WAL mode · short-lived connections</p>
      </div>
      <div class="system-fact">
        <span>GhostBrain</span>
        <strong>{{ session.health?.aiStatus ?? session.ai?.status ?? 'unknown' }}</strong>
        <p>{{ session.health?.aiModel ?? session.ai?.model ?? 'No model reported' }}</p>
      </div>
      <div class="system-fact">
        <span>Version</span>
        <strong>{{ session.system?.version ?? session.health?.version ?? '—' }}</strong>
        <p>{{ session.system?.bind ?? 'local-only by default' }}</p>
      </div>
      <div class="system-fact">
        <span>Scan limits</span>
        <strong class="numeric">{{ session.system?.maxScanFiles ?? '—' }} files</strong>
        <p>Depth {{ session.system?.maxScanDepth ?? '—' }} · log read cap {{ bytes(session.system?.maxLogBytes) }}</p>
      </div>
      <div class="system-fact">
        <span>API token</span>
        <strong>{{ session.system?.apiTokenEnabled ? 'Enabled' : 'Not required' }}</strong>
        <p>Tokens stay in this tab's memory and are never stored.</p>
      </div>
    </div>
    <div class="panel__body system-paths">
      <p><span>Data directory</span><code>{{ session.settings?.dataDir ?? session.system?.dataDir ?? '—' }}</code></p>
      <p><span>Database</span><code>{{ session.settings?.databasePath ?? session.system?.databasePath ?? '—' }}</code></p>
      <p v-if="session.health?.timestamp" class="faint tiny">Last health check {{ humanDate(session.health.timestamp) }}</p>
    </div>
  </section>
</template>

<style scoped>
.pulse-error {
  margin: 0 0 1rem;
  padding: 0.65rem 0.85rem;
  border: 1px solid color-mix(in srgb, var(--danger) 30%, var(--line));
  border-left: 2px solid var(--danger);
  border-radius: var(--radius-sm);
  color: var(--danger);
  font-size: 0.78rem;
  line-height: 1.5;
}
.pulse-error--flush {
  margin: 0;
}
.pulse-loading {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin-bottom: 1.3rem;
}
.savings-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(280px, 0.75fr);
  gap: 1.5rem;
  padding: 1.5rem 1.6rem;
  margin-bottom: 1.3rem;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent) 7%, transparent), transparent 55%),
    var(--surface);
}
.savings-hero__value {
  margin: 0.55rem 0 0;
  font-family: var(--font-display);
  font-size: clamp(2.8rem, 6vw, 4.6rem);
  font-weight: 400;
  line-height: 0.95;
  letter-spacing: -0.04em;
}
.savings-hero__copy {
  margin: 0.8rem 0 0;
  max-width: 56ch;
  color: var(--text-muted);
  font-size: 0.84rem;
  line-height: 1.6;
}
.savings-hero__method {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  flex-wrap: wrap;
  margin-top: 1rem;
}
.savings-hero__method .mono {
  color: var(--text-faint);
  font-size: 0.62rem;
}
.savings-hero__aside {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 1rem;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--surface-2) 55%, transparent);
}
.savings-figure {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  padding-bottom: 0.55rem;
  border-bottom: 1px solid color-mix(in srgb, var(--line) 70%, transparent);
}
.savings-figure span {
  font-family: var(--font-mono);
  font-size: 0.6rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-faint);
}
.savings-figure strong {
  font-size: 0.86rem;
  font-weight: 550;
}
.savings-hero__disclaimer {
  margin: 0.2rem 0 0;
  color: var(--text-faint);
  font-size: 0.68rem;
  line-height: 1.55;
}
.pulse-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(290px, 0.8fr);
  gap: 1.3rem;
  align-items: start;
  margin-bottom: 1.3rem;
}
.panel__link {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  border: 0;
  background: none;
  padding: 0;
  font-family: var(--font-mono);
  font-size: 0.63rem;
  color: var(--accent);
  white-space: nowrap;
}
.panel__hint {
  font-family: var(--font-mono);
  font-size: 0.6rem;
  color: var(--text-faint);
}
.savings-lines {
  padding: 0.8rem 1rem 0.9rem;
}
.savings-line + .savings-line {
  margin-top: 1rem;
}
.savings-line__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.8rem;
}
.savings-line__head strong {
  display: block;
  font-size: 0.8rem;
  font-weight: 550;
}
.savings-line__numbers {
  display: block;
  margin-top: 0.12rem;
  color: var(--text-faint);
  font-size: 0.6rem;
}
.savings-line__saved {
  font-family: var(--font-mono);
  font-size: 0.72rem;
  color: var(--accent);
  white-space: nowrap;
}
.savings-line__saved--negative {
  color: var(--warn);
}
.savings-bar {
  position: relative;
  height: 0.55rem;
  margin-top: 0.45rem;
  border-radius: 999px;
  background: var(--surface-3);
  overflow: hidden;
}
.savings-bar__manual {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: 999px;
  background: color-mix(in srgb, var(--info) 40%, var(--surface-3));
}
.savings-bar__measured {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: 999px;
  background: var(--accent);
  opacity: 0.85;
}
.savings-line__basis {
  margin: 0.45rem 0 0;
  color: var(--text-faint);
  font-size: 0.68rem;
  line-height: 1.5;
}
.savings-legend {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
  margin-top: 1rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--line);
  color: var(--text-faint);
  font-size: 0.65rem;
}
.savings-legend span {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}
.savings-legend i {
  display: inline-block;
  width: 0.7rem;
  height: 0.4rem;
  border-radius: 999px;
}
.legend-manual {
  background: color-mix(in srgb, var(--info) 40%, var(--surface-3));
}
.legend-measured {
  background: var(--accent);
}
.pulse-insights {
  display: flex;
  flex-direction: column;
}
.pulse-insight {
  display: flex;
  gap: 0.65rem;
  padding: 0.8rem 1rem;
  border-bottom: 1px solid var(--line);
}
.pulse-insight:last-child {
  border-bottom: 0;
}
.pulse-insight__icon {
  display: grid;
  place-items: center;
  width: 1.8rem;
  height: 1.8rem;
  border-radius: 50%;
  border: 1px solid var(--line);
  color: var(--text-muted);
  flex: none;
}
.pulse-insight__icon--danger {
  color: var(--danger);
  border-color: color-mix(in srgb, var(--danger) 30%, var(--line));
}
.pulse-insight__icon--warning {
  color: var(--warn);
  border-color: color-mix(in srgb, var(--warn) 30%, var(--line));
}
.pulse-insight strong {
  display: block;
  font-size: 0.78rem;
  font-weight: 550;
}
.pulse-insight p {
  margin: 0.2rem 0 0;
  color: var(--text-muted);
  font-size: 0.72rem;
  line-height: 1.5;
}
.duplicate-panel {
  margin-bottom: 1.3rem;
}
.duplicate-panel__sub {
  margin: 0.25rem 0 0;
  color: var(--text-faint);
  font-size: 0.72rem;
}
.duplicate-form {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  border-bottom: 1px solid var(--line);
}
.duplicate-form__options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
}
.duplicate-placeholder {
  padding: 1rem 1.15rem 1.2rem;
}
.duplicate-placeholder p {
  margin: 0;
  max-width: 78ch;
  color: var(--text-faint);
  font-size: 0.74rem;
  line-height: 1.6;
}
.duplicate-report {
  padding: 1.1rem 1.15rem 1.2rem;
}
.duplicate-report__summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0.8rem;
  padding-bottom: 0.95rem;
  border-bottom: 1px solid var(--line);
}
.duplicate-report__summary span {
  display: block;
  font-family: var(--font-mono);
  font-size: 0.56rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-faint);
}
.duplicate-report__summary strong {
  display: block;
  margin-top: 0.25rem;
  font-family: var(--font-display);
  font-size: 1.15rem;
  font-weight: 460;
}
.duplicate-report__notice {
  margin: 0.9rem 0;
}
.duplicate-groups {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}
.duplicate-group {
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--surface-2) 40%, transparent);
  overflow: hidden;
}
.duplicate-group__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.9rem;
  padding: 0.7rem 0.8rem;
  border-bottom: 1px solid var(--line);
}
.duplicate-group__head > div:first-child {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
  min-width: 0;
}
.duplicate-group__head strong {
  font-size: 0.76rem;
  font-weight: 550;
}
.duplicate-group__size {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.1rem;
  text-align: right;
  flex: none;
}
.duplicate-group__size .mono {
  color: var(--text-faint);
  font-size: 0.58rem;
}
.duplicate-group__overlap {
  color: var(--accent);
  font-size: 0.68rem;
}
.duplicate-files {
  list-style: none;
  margin: 0;
  padding: 0.35rem 0.8rem 0.45rem;
}
.duplicate-files li {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.28rem 0;
  color: var(--text-muted);
  font-size: 0.72rem;
}
.duplicate-files .icon {
  color: var(--text-faint);
}
.duplicate-files__path {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text);
}
.duplicate-files__meta {
  color: var(--text-faint);
  font-size: 0.62rem;
  white-space: nowrap;
}
.system-panel {
  overflow: hidden;
}
.system-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.system-fact {
  padding: 1rem 1.15rem;
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}
.system-fact:nth-child(3n) {
  border-right: 0;
}
.system-fact > span {
  display: block;
  font-family: var(--font-mono);
  font-size: 0.57rem;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  color: var(--text-faint);
}
.system-fact > strong {
  display: block;
  margin-top: 0.35rem;
  font-size: 0.84rem;
  font-weight: 550;
}
.system-fact p {
  margin: 0.25rem 0 0;
  color: var(--text-faint);
  font-size: 0.66rem;
  line-height: 1.45;
  overflow-wrap: anywhere;
}
.system-paths {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  background: color-mix(in srgb, var(--surface-2) 35%, transparent);
}
.system-paths p {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  margin: 0;
  font-size: 0.7rem;
}
.system-paths span {
  font-family: var(--font-mono);
  font-size: 0.58rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-faint);
  white-space: nowrap;
}
.system-paths code {
  font-size: 0.62rem;
  color: var(--text-muted);
  overflow-wrap: anywhere;
  text-align: right;
}
@media (max-width: 1150px) {
  .pulse-grid {
    grid-template-columns: 1fr;
  }
  .savings-hero {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 860px) {
  .duplicate-report__summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .system-grid {
    grid-template-columns: 1fr 1fr;
  }
  .system-fact:nth-child(3n) {
    border-right: 1px solid var(--line);
  }
  .system-fact:nth-child(2n) {
    border-right: 0;
  }
}
@media (max-width: 620px) {
  .duplicate-group__head {
    flex-direction: column;
  }
  .duplicate-group__size {
    align-items: flex-start;
    text-align: left;
  }
  .duplicate-files li {
    align-items: flex-start;
    flex-wrap: wrap;
  }
  .duplicate-files__path {
    white-space: normal;
    overflow-wrap: anywhere;
  }
  .system-grid {
    grid-template-columns: 1fr;
  }
  .system-fact {
    border-right: 0;
  }
  .system-paths p {
    flex-direction: column;
    gap: 0.2rem;
  }
  .system-paths code {
    text-align: left;
  }
}
</style>
