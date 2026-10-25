<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, type RouteLocationRaw } from 'vue-router'
import { api, downloadReport } from '@/api/client'
import type { ActivityItem, ReportSummary, ReportView, RunSummary } from '@/api/types'
import { errorMessage } from '@/lib/errors'
import { duration, humanDate, relativeTime, titleCase } from '@/lib/format'
import MarkdownLite from '@/components/MarkdownLite.vue'
import EmptyState from '@/components/EmptyState.vue'
import Icon from '@/components/Icon.vue'
import Modal from '@/components/Modal.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusPill from '@/components/StatusPill.vue'

type Tab = 'runs' | 'activity' | 'reports'

const route = useRoute()

const tab = ref<Tab>(parseTab(route.query.tab))
const runs = ref<RunSummary[]>([])
const runsLoading = ref(false)
const runsError = ref('')
const runSearch = ref('')
const runStatus = ref('ALL')

const activity = ref<ActivityItem[]>([])
const activityLoading = ref(false)
const activityError = ref('')
const activityKind = ref('')
const activityPage = ref(1)
const activityPageSize = 25
const activityTotal = ref(0)

const reports = ref<ReportSummary[]>([])
const reportsLoading = ref(false)
const reportsError = ref('')
const reportSearch = ref('')
const reportModalOpen = ref(false)
const selectedReport = ref<ReportView | null>(null)
const reportLoading = ref(false)
const reportError = ref('')
const exporting = ref('')

let runsLoaded = false
let activityLoaded = false
let reportsLoaded = false

const activityKinds = [
  'DIRECTORY',
  'ORGANIZATION',
  'WORKFLOW',
  'APPROVAL',
  'REPORT',
  'RECORDING',
  'DEMO',
  'DATA',
]

const tabItems: { id: Tab; label: string; icon: string }[] = [
  { id: 'runs', label: 'Runs', icon: 'play' },
  { id: 'activity', label: 'Activity', icon: 'pulse' },
  { id: 'reports', label: 'Reports', icon: 'file' },
]

const runStatuses = [
  'ALL',
  'QUEUED',
  'RUNNING',
  'WAITING_FOR_APPROVAL',
  'COMPLETED',
  'PARTIALLY_COMPLETED',
  'FAILED',
  'CANCELLED',
]

const filteredRuns = computed(() => {
  const query = runSearch.value.trim().toLowerCase()
  return runs.value.filter((run) => {
    const matchesStatus = runStatus.value === 'ALL' || run.status === runStatus.value
    const matchesSearch =
      !query ||
      run.workflowName.toLowerCase().includes(query) ||
      (run.summary ?? '').toLowerCase().includes(query)
    return matchesStatus && matchesSearch
  })
})

const filteredReports = computed(() => {
  const query = reportSearch.value.trim().toLowerCase()
  if (!query) return reports.value
  return reports.value.filter(
    (report) =>
      report.title.toLowerCase().includes(query) ||
      report.kind.toLowerCase().includes(query) ||
      (report.sourceRelative ?? '').toLowerCase().includes(query) ||
      (report.model ?? '').toLowerCase().includes(query),
  )
})

const activityPages = computed(() => Math.max(1, Math.ceil(activityTotal.value / activityPageSize)))

const tabCounts = computed(() => ({
  runs: runs.value.length,
  activity: activityTotal.value,
  reports: reports.value.length,
}))

function parseTab(value: unknown): Tab {
  return value === 'activity' || value === 'reports' ? value : 'runs'
}

function selectActivityKind(event: Event) {
  activityKind.value = (event.target as HTMLSelectElement).value
  void changeActivityKind()
}

function activityIcon(kind: string): string {
  switch (kind.toUpperCase()) {
    case 'ORGANIZATION':
      return 'sort'
    case 'WORKFLOW':
      return 'play'
    case 'APPROVAL':
      return 'shield'
    case 'RECORDING':
      return 'record'
    case 'DIRECTORY':
      return 'folder'
    case 'REPORT':
      return 'file'
    case 'DEMO':
      return 'spark'
    case 'DATA':
      return 'database'
    default:
      return 'dot'
  }
}

function activityLink(item: ActivityItem): RouteLocationRaw | null {
  if (!item.entityId) return null
  switch (item.kind.toUpperCase()) {
    case 'WORKFLOW':
    case 'APPROVAL':
      return { name: 'run-detail', params: { runId: item.entityId } }
    case 'REPORT':
      return { name: 'ghost-dev', query: { report: item.entityId } }
    case 'RECORDING':
      return { name: 'ghost-capture' }
    default:
      return null
  }
}

function reportKindLabel(kind: string): string {
  if (kind === 'LOG_ANALYSIS') return 'Log evidence'
  if (kind === 'WORKFLOW') return 'Workflow report'
  return titleCase(kind)
}

async function loadRuns() {
  runsLoading.value = true
  runsError.value = ''
  try {
    runs.value = await api.runs(100)
    runsLoaded = true
  } catch (cause) {
    runsError.value = errorMessage(cause, 'Run history could not be loaded.')
  } finally {
    runsLoading.value = false
  }
}

async function loadActivity(page = activityPage.value) {
  activityLoading.value = true
  activityError.value = ''
  try {
    const result = await api.activity(page, activityPageSize, activityKind.value)
    activity.value = result.items ?? []
    activityPage.value = result.page
    activityTotal.value = result.total
    activityLoaded = true
  } catch (cause) {
    activityError.value = errorMessage(cause, 'Activity history could not be loaded.')
  } finally {
    activityLoading.value = false
  }
}

async function loadReports() {
  reportsLoading.value = true
  reportsError.value = ''
  try {
    reports.value = await api.reports(100)
    reportsLoaded = true
  } catch (cause) {
    reportsError.value = errorMessage(cause, 'Reports could not be loaded.')
  } finally {
    reportsLoading.value = false
  }
}

async function selectTab(next: Tab) {
  tab.value = next
  if (next === 'runs' && !runsLoaded) await loadRuns()
  if (next === 'activity' && !activityLoaded) await loadActivity(1)
  if (next === 'reports' && !reportsLoaded) await loadReports()
}

async function refreshCurrent() {
  if (tab.value === 'runs') await loadRuns()
  else if (tab.value === 'activity') await loadActivity(activityPage.value)
  else await loadReports()
}

async function changeActivityKind() {
  activityPage.value = 1
  await loadActivity(1)
}

async function activityPageTo(page: number) {
  if (page < 1 || page > activityPages.value || page === activityPage.value) return
  await loadActivity(page)
}

async function openReport(report: ReportSummary) {
  reportModalOpen.value = true
  reportLoading.value = true
  reportError.value = ''
  selectedReport.value = null
  try {
    selectedReport.value = await api.report(report.id)
  } catch (cause) {
    reportError.value = errorMessage(cause, 'That report could not be opened.')
  } finally {
    reportLoading.value = false
  }
}

function closeReport() {
  reportModalOpen.value = false
  selectedReport.value = null
  reportError.value = ''
}

async function exportReport(report: ReportSummary) {
  exporting.value = report.id
  reportsError.value = ''
  try {
    await downloadReport(report)
  } catch (cause) {
    reportsError.value = errorMessage(cause, 'The report could not be exported.')
  } finally {
    exporting.value = ''
  }
}

onMounted(async () => {
  await loadRuns()
  await loadReports()
  if (tab.value === 'activity') await loadActivity(1)
})
</script>

<template>
  <PageHeader
    eyebrow="History"
    title="The record stays on this machine."
    description="Every run, decision, report, and activity entry is stored in local SQLite. History is append-only from the app's perspective: nothing here is fabricated or silently rewritten."
  >
    <template #actions>
      <RouterLink to="/approvals" class="btn btn--ghost btn--sm">
        <Icon name="shield" :size="14" />
        Approvals
      </RouterLink>
      <button class="btn btn--primary btn--sm" type="button" :disabled="runsLoading || activityLoading || reportsLoading" @click="refreshCurrent">
        <span v-if="runsLoading || activityLoading || reportsLoading" class="spinner" />
        <Icon v-else name="refresh" :size="15" />
        Refresh
      </button>
    </template>
  </PageHeader>

  <div class="history-tabs" role="tablist" aria-label="History sections">
    <button
      v-for="item in tabItems"
      :key="item.id"
      class="history-tab"
      :class="{ 'history-tab--active': tab === item.id }"
      type="button"
      role="tab"
      :aria-selected="tab === item.id"
      @click="selectTab(item.id)"
    >
      <Icon :name="item.icon" :size="15" />
      {{ item.label }}
      <span class="history-tab__count numeric">
        {{ item.id === 'runs' ? tabCounts.runs : item.id === 'activity' ? tabCounts.activity : tabCounts.reports }}
      </span>
    </button>
  </div>

  <section v-if="tab === 'runs'" class="history-panel" aria-label="Workflow runs">
    <div class="history-toolbar">
      <div class="history-search">
        <Icon name="search" :size="15" />
        <input v-model="runSearch" type="search" placeholder="Search workflow or summary…" aria-label="Search runs" />
      </div>
      <select v-model="runStatus" aria-label="Filter runs by status">
        <option v-for="status in runStatuses" :key="status" :value="status">
          {{ status === 'ALL' ? 'All statuses' : titleCase(status) }}
        </option>
      </select>
      <span class="history-count mono">{{ filteredRuns.length }} shown</span>
    </div>

    <p v-if="runsError" class="history-error" role="alert">{{ runsError }}</p>

    <div v-if="runs.length" class="panel">
      <div class="table-wrap">
        <table class="table history-table">
          <thead>
            <tr>
              <th>Workflow</th>
              <th>Status</th>
              <th>Mode</th>
              <th>Steps</th>
              <th>Duration</th>
              <th>Started</th>
              <th><span class="sr-only">Open</span></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="run in filteredRuns" :key="run.id">
              <td>
                <RouterLink :to="{ name: 'run-detail', params: { runId: run.id } }" class="cell-main">
                  {{ run.workflowName }}
                </RouterLink>
                <span class="cell-sub">{{ run.summary || 'No summary recorded' }}</span>
              </td>
              <td><StatusPill :status="run.status" dot /></td>
              <td class="muted small nowrap">{{ run.dryRun ? 'Dry run' : 'Live' }}</td>
              <td class="muted small nowrap numeric">
                {{ run.succeededSteps }}/{{ run.totalSteps }}
                <span v-if="run.failedSteps" class="history-failed"> · {{ run.failedSteps }} failed</span>
              </td>
              <td class="muted small nowrap">{{ duration(run.durationMs) }}</td>
              <td class="muted small nowrap">{{ relativeTime(run.startedAt) }}</td>
              <td>
                <RouterLink :to="{ name: 'run-detail', params: { runId: run.id } }" class="history-open" aria-label="Open run">
                  <Icon name="chevron" :size="14" />
                </RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="filteredRuns.length === 0" class="history-nomatch">
        <p>No runs match this search or status. Try a different filter.</p>
        <button class="btn btn--sm" type="button" @click="runSearch = ''; runStatus = 'ALL'">Clear filters</button>
      </div>
    </div>

    <EmptyState
      v-else-if="!runsLoading"
      icon="play"
      title="No workflow runs recorded yet"
      description="Dry-run an example workflow to create the first real record. GhostShift never invents history to make this screen look busy."
    >
      <RouterLink to="/replay" class="btn btn--sm">Open GhostReplay</RouterLink>
    </EmptyState>
    <div v-else class="history-loading"><span class="spinner" /> Loading runs…</div>
  </section>

  <section v-else-if="tab === 'activity'" class="history-panel" aria-label="Activity history">
    <div class="history-toolbar">
      <select :value="activityKind" aria-label="Filter activity by kind" @change="selectActivityKind">
        <option value="">All activity kinds</option>
        <option v-for="kind in activityKinds" :key="kind" :value="kind">{{ titleCase(kind) }}</option>
      </select>
      <span class="history-count mono">{{ activityTotal }} entries</span>
      <button class="btn btn--ghost btn--sm toolbar--end" type="button" :disabled="activityLoading" @click="loadActivity(activityPage)">
        <span v-if="activityLoading" class="spinner" />
        <Icon v-else name="refresh" :size="14" />
        Refresh
      </button>
    </div>

    <p v-if="activityError" class="history-error" role="alert">{{ activityError }}</p>

    <div v-if="activity.length" class="panel activity-panel">
      <div class="activity-list">
        <component
          :is="activityLink(item) ? 'RouterLink' : 'div'"
          v-for="item in activity"
          :key="item.id"
          :to="activityLink(item) ?? undefined"
          class="activity-entry"
          :class="{ 'activity-entry--link': activityLink(item) }"
        >
          <span class="activity-entry__icon"><Icon :name="activityIcon(item.kind)" :size="15" /></span>
          <div class="activity-entry__copy">
            <div class="activity-entry__title">
              <strong>{{ item.title }}</strong>
              <span class="chip">{{ titleCase(item.kind) }}</span>
            </div>
            <p>{{ item.detail }}</p>
          </div>
          <span class="activity-entry__time">{{ humanDate(item.at) }}</span>
          <Icon v-if="activityLink(item)" name="chevron" :size="13" class="activity-entry__arrow" />
        </component>
      </div>
      <div class="history-pagination">
        <button class="btn btn--sm btn--ghost" type="button" :disabled="activityPage <= 1 || activityLoading" @click="activityPageTo(activityPage - 1)">
          Previous
        </button>
        <span class="mono">Page {{ activityPage }} of {{ activityPages }}</span>
        <button class="btn btn--sm btn--ghost" type="button" :disabled="activityPage >= activityPages || activityLoading" @click="activityPageTo(activityPage + 1)">
          Next
        </button>
      </div>
    </div>
    <EmptyState
      v-else-if="!activityLoading"
      icon="pulse"
      title="No activity in this view"
      description="Activity is written when you register folders, run workflows, decide approvals, generate reports, record workflows, seed demos, or clear local data."
    />
    <div v-else class="history-loading"><span class="spinner" /> Loading activity…</div>
  </section>

  <section v-else class="history-panel" aria-label="Reports">
    <div class="history-toolbar">
      <div class="history-search">
        <Icon name="search" :size="15" />
        <input v-model="reportSearch" type="search" placeholder="Search title, kind, source, or model…" aria-label="Search reports" />
      </div>
      <span class="history-count mono">{{ filteredReports.length }} shown</span>
      <button class="btn btn--ghost btn--sm toolbar--end" type="button" :disabled="reportsLoading" @click="loadReports">
        <span v-if="reportsLoading" class="spinner" />
        <Icon v-else name="refresh" :size="14" />
        Refresh
      </button>
    </div>

    <p v-if="reportsError" class="history-error" role="alert">{{ reportsError }}</p>

    <div v-if="reports.length" class="report-list panel">
      <div v-for="report in filteredReports" :key="report.id" class="report-row">
        <span class="report-row__icon"><Icon name="file" :size="16" /></span>
        <button class="report-row__copy" type="button" @click="openReport(report)">
          <strong>{{ report.title }}</strong>
          <span>
            {{ reportKindLabel(report.kind) }}
            <template v-if="report.sourceRelative"> · {{ report.sourceRelative }}</template>
            <template v-if="report.aiUsed"> · {{ report.model || 'local model' }}</template>
            <template v-else> · deterministic</template>
          </span>
        </button>
        <span class="report-row__date">{{ relativeTime(report.createdAt) }}</span>
        <button class="btn btn--sm" type="button" @click="openReport(report)">Open</button>
        <button
          class="icon-btn"
          type="button"
          :disabled="exporting === report.id"
          aria-label="Export report as Markdown"
          @click="exportReport(report)"
        >
          <span v-if="exporting === report.id" class="spinner" />
          <Icon v-else name="download" :size="15" />
        </button>
      </div>
      <div v-if="filteredReports.length === 0" class="history-nomatch">
        <p>No reports match this search.</p>
        <button class="btn btn--sm" type="button" @click="reportSearch = ''">Clear search</button>
      </div>
    </div>
    <EmptyState
      v-else-if="!reportsLoading"
      icon="file"
      title="No reports yet"
      description="Analyze a log in GhostDev or include a report step in a workflow. Generated Markdown is stored locally and listed here."
    >
      <RouterLink to="/dev" class="btn btn--sm">Open GhostDev</RouterLink>
    </EmptyState>
    <div v-else class="history-loading"><span class="spinner" /> Loading reports…</div>
  </section>

  <p class="history-foot">
    <Icon name="lock" :size="13" />
    History lives in the local GhostShift database. Exports contain metadata and report Markdown, never your file contents.
  </p>

  <Modal
    :open="reportModalOpen"
    :title="selectedReport?.title || 'Report'"
    :subtitle="selectedReport ? `${reportKindLabel(selectedReport.kind)} · ${humanDate(selectedReport.createdAt)}` : ''"
    wide
    @close="closeReport"
  >
    <div v-if="reportLoading" class="history-loading"><span class="spinner" /> Loading report…</div>
    <p v-else-if="reportError" class="history-error" role="alert">{{ reportError }}</p>
    <template v-else-if="selectedReport">
      <div class="report-meta">
        <span class="chip" :class="{ 'chip--accent': selectedReport.aiUsed }">
          {{ selectedReport.aiUsed ? `GhostBrain assisted · ${selectedReport.model || 'model not recorded'}` : 'Deterministic only' }}
        </span>
        <span v-if="selectedReport.sourceRelative" class="mono">{{ selectedReport.sourceRelative }}</span>
      </div>
      <MarkdownLite :content="selectedReport.contentMarkdown" />
    </template>
    <template #footer>
      <button class="btn btn--ghost" type="button" @click="closeReport">Close</button>
      <button
        v-if="selectedReport"
        class="btn btn--primary"
        type="button"
        :disabled="exporting === selectedReport.id"
        @click="exportReport(selectedReport)"
      >
        <span v-if="exporting === selectedReport.id" class="spinner" />
        <Icon v-else name="download" :size="15" />
        Export Markdown
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.history-tabs {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding-bottom: 0.4rem;
  border-bottom: 1px solid var(--line);
  margin-bottom: 1.2rem;
  overflow-x: auto;
}
.history-tab {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.55rem 0.8rem;
  border: 1px solid transparent;
  border-bottom: 0;
  border-radius: var(--radius-sm) var(--radius-sm) 0 0;
  background: transparent;
  color: var(--text-muted);
  font-size: 0.8rem;
  font-weight: 550;
  white-space: nowrap;
}
.history-tab:hover {
  color: var(--text);
  background: var(--surface-2);
}
.history-tab--active {
  color: var(--text);
  background: var(--surface-2);
  border-color: var(--line);
  margin-bottom: -1px;
}
.history-tab__count {
  display: inline-grid;
  place-items: center;
  min-width: 1.25rem;
  height: 1.15rem;
  padding: 0 0.3rem;
  border-radius: 0;
  background: var(--surface-3);
  color: var(--text-faint);
  font-family: var(--font-sans);
  font-size: 0.6rem;
  font-weight: 650;
}
.history-tab--active .history-tab__count {
  background: color-mix(in srgb, var(--accent) 16%, transparent);
  color: var(--accent);
}
.history-toolbar {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
  margin-bottom: 0.9rem;
}
.history-search {
  position: relative;
  display: flex;
  align-items: center;
  min-width: 250px;
  flex: 1;
  max-width: 460px;
}
.history-search .icon {
  position: absolute;
  left: 0.65rem;
  color: var(--text-faint);
  pointer-events: none;
}
.history-search input {
  padding-left: 2rem;
}
.history-toolbar select {
  width: auto;
  min-width: 170px;
}
.history-count {
  color: var(--text-faint);
  font-size: 0.62rem;
  white-space: nowrap;
}
.toolbar--end {
  margin-left: auto;
}
.history-error {
  margin: 0 0 0.9rem;
  padding: 0.65rem 0.85rem;
  border: 1px solid color-mix(in srgb, var(--danger) 30%, var(--line));
  border-left: 2px solid var(--danger);
  border-radius: var(--radius-sm);
  color: var(--danger);
  font-size: 0.78rem;
  line-height: 1.5;
}
.history-table .cell-main {
  color: var(--text);
  font-weight: 550;
}
.history-table .cell-main:hover {
  color: var(--accent);
}
.history-failed {
  color: var(--danger);
}
.history-open {
  display: inline-grid;
  place-items: center;
  width: 1.7rem;
  height: 1.7rem;
  border-radius: var(--radius-sm);
  color: var(--text-faint);
}
.history-open:hover {
  background: var(--surface-3);
  color: var(--accent);
}
.history-nomatch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1.15rem;
  border-top: 1px solid var(--line);
}
.history-nomatch p {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.78rem;
}
.history-loading {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 1.2rem;
  color: var(--text-muted);
  font-size: 0.8rem;
}
.activity-panel {
  overflow: hidden;
}
.activity-list {
  display: flex;
  flex-direction: column;
}
.activity-entry {
  display: flex;
  align-items: flex-start;
  gap: 0.7rem;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid color-mix(in srgb, var(--line) 70%, transparent);
  color: var(--text);
}
.activity-entry:last-child {
  border-bottom: 0;
}
.activity-entry--link:hover {
  background: var(--surface-2);
  color: var(--text);
}
.activity-entry__icon {
  display: grid;
  place-items: center;
  width: 1.75rem;
  height: 1.75rem;
  border: 1px solid var(--line-strong);
  border-radius: 0;
  color: var(--text-faint);
  flex: none;
  margin-top: 0.05rem;
}
.activity-entry__copy {
  min-width: 0;
  flex: 1;
}
.activity-entry__title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}
.activity-entry__title strong {
  font-size: 0.78rem;
  font-weight: 550;
}
.activity-entry__copy p {
  margin: 0.2rem 0 0;
  color: var(--text-muted);
  font-size: 0.72rem;
  line-height: 1.45;
}
.activity-entry__time {
  font-family: var(--font-mono);
  font-size: 0.6rem;
  color: var(--text-faint);
  white-space: nowrap;
  padding-top: 0.2rem;
}
.activity-entry__arrow {
  color: var(--text-faint);
  margin-top: 0.35rem;
}
.history-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 0.8rem;
  border-top: 1px solid var(--line);
}
.history-pagination .mono {
  color: var(--text-faint);
  font-size: 0.62rem;
}
.report-list {
  overflow: hidden;
}
.report-row {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.7rem 0.9rem;
  border-bottom: 1px solid color-mix(in srgb, var(--line) 70%, transparent);
}
.report-row:last-child {
  border-bottom: 0;
}
.report-row:hover {
  background: color-mix(in srgb, var(--surface-3) 35%, transparent);
}
.report-row__icon {
  display: grid;
  place-items: center;
  width: 1.8rem;
  height: 1.8rem;
  border: 1px solid var(--line-strong);
  border-radius: 0;
  color: var(--signal);
  flex: none;
}
.report-row__copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  flex: 1;
  padding: 0;
  border: 0;
  background: none;
  text-align: left;
}
.report-row__copy strong {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.78rem;
  font-weight: 550;
  color: var(--text);
}
.report-row__copy span {
  margin-top: 0.1rem;
  color: var(--text-faint);
  font-size: 0.64rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
.report-row__date {
  color: var(--text-faint);
  font-family: var(--font-mono);
  font-size: 0.6rem;
  white-space: nowrap;
}
.report-meta {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  flex-wrap: wrap;
  margin-bottom: 1rem;
  padding-bottom: 0.8rem;
  border-bottom: 1px solid var(--line);
}
.report-meta .mono {
  color: var(--text-faint);
  font-size: 0.64rem;
}
.history-foot {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin: 1.4rem 0 0;
  padding-top: 0.9rem;
  border-top: 1px solid var(--line);
  color: var(--text-faint);
  font-size: 0.66rem;
  line-height: 1.5;
}
@media (max-width: 720px) {
  .history-search {
    min-width: 100%;
    max-width: none;
  }
  .history-toolbar select {
    width: 100%;
  }
  .toolbar--end {
    margin-left: 0;
  }
  .activity-entry {
    flex-wrap: wrap;
  }
  .activity-entry__time {
    padding-left: 2.45rem;
    width: 100%;
  }
  .report-row {
    flex-wrap: wrap;
  }
  .report-row__copy {
    flex-basis: calc(100% - 3rem);
  }
  .report-row__date {
    padding-left: 2.5rem;
  }
}
</style>
